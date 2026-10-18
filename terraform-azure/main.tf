# Ambiente de teste do backend no Azure (free tier): Container Apps com scale a
# zero + PostgreSQL Flexible Server. Espelha o que a AWS roda em EC2 + Docker
# Compose, sem Caddy e sem Lambda de liga/desliga.
#
# Sem VNet de proposito: um ambiente de Container Apps numa VNet propria cria um
# Load Balancer Standard e um IP publico Standard (~US$ 22/mes, fora do free
# tier), mesmo com ingress interno. Na rede gerenciada nao ha esse custo, e o
# preco e o Postgres com endpoint publico, protegido por TLS, senha e firewall
# liberando so os IPs de saida dos Container Apps.

locals {
  # Os dois apps se enxergam pelo FQDN derivado do dominio do ambiente + nome
  # do app. Montar a URL a partir do nome (em vez de referenciar o outro
  # recurso) evita dependencia circular: o backend precisa da URL do agente e
  # o agente, da do backend.
  backend_name  = "${var.name_prefix}-api"
  ai_agent_name = "${var.name_prefix}-ai-agent"
  backend_url   = "https://${local.backend_name}.${azurerm_container_app_environment.main.default_domain}"
  ai_agent_url  = "https://${local.ai_agent_name}.internal.${azurerm_container_app_environment.main.default_domain}"

  # Mesmas opcoes do backend JVM na AWS (deploy/estado/lib-swap.sh), com heap maior.
  backend_java_opts = "-XX:+UseSerialGC -Xmx384m -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m"

  # Mesmo teto de memoria do agente na AWS (deploy/lib-swap.sh do ai-agent).
  ai_agent_java_opts = "-XX:+UseSerialGC -Xmx128m -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m"
}

resource "random_string" "suffix" {
  length  = 6
  upper   = false
  special = false
}

# Alfanumerica de proposito: vai pra URL JDBC e pra env vars, sem precisar de
# escape.
resource "random_password" "postgres" {
  length  = 32
  special = false
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

# Compartilhada entre backend e agente (o backend e o BFF que guarda a chave).
resource "random_password" "ask_api_key" {
  length  = 48
  special = false
}

resource "azurerm_resource_group" "main" {
  name     = "${var.name_prefix}-rg"
  location = var.location
}

# ---- Banco -----------------------------------------------------------------

resource "azurerm_postgresql_flexible_server" "main" {
  name                = "${var.name_prefix}-pg-${random_string.suffix.result}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  version             = "16"

  # B1ms + 32 GB e o que o free tier de 12 meses cobre (750h/mes).
  sku_name   = "B_Standard_B1ms"
  storage_mb = 32768

  administrator_login    = "estado"
  administrator_password = random_password.postgres.result

  public_network_access_enabled = true

  backup_retention_days        = 7
  geo_redundant_backup_enabled = false

  lifecycle {
    # O Azure escolhe a zona de disponibilidade na criacao; sem isso o proximo
    # plan tentaria "corrigir" pra null e recriar o servidor.
    ignore_changes = [zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "estado" {
  name      = "estado"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# Um IP por regra. Os IPs de saida so existem depois que os apps sao criados, e
# for_each exige chaves conhecidas no plan: por isso entram por variavel, num
# segundo apply (ver README). O Azure avisa que esses IPs podem mudar com o
# tempo; se o backend perder o banco, repita o passo.
resource "azurerm_postgresql_flexible_server_firewall_rule" "container_apps" {
  for_each = toset(var.container_apps_outbound_ips)

  name             = "aca-${replace(each.value, ".", "-")}"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = each.value
  end_ip_address   = each.value
}

# ---- Observabilidade basica do ambiente ------------------------------------

resource "azurerm_log_analytics_workspace" "main" {
  name                = "${var.name_prefix}-logs"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30

  # Teto diario de ingestao: a cota gratuita e 5 GB/mes, e um loop de erro
  # nao deve virar conta.
  daily_quota_gb = 0.1
}

# ---- Container Apps --------------------------------------------------------

resource "azurerm_container_app_environment" "main" {
  name                       = "${var.name_prefix}-env"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id

  workload_profile {
    name                  = "Consumption"
    workload_profile_type = "Consumption"
  }
}

resource "azurerm_container_app" "ai_agent" {
  name                         = local.ai_agent_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"

  secret {
    name  = "gemini-api-key"
    value = var.gemini_api_key
  }

  secret {
    name  = "ask-api-key"
    value = random_password.ask_api_key.result
  }

  # Interno de proposito: so o backend (BFF) fala com o agente. Exposto
  # publicamente, quem tivesse a chave poderia forjar X-Forwarded-For e furar o
  # rate limit por IP.
  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 0
    max_replicas = 1

    container {
      name   = "ai-agent"
      image  = var.ai_agent_image
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = local.ai_agent_java_opts
      }
      env {
        name  = "ESTADO_API_BASE_URL"
        value = local.backend_url
      }
      env {
        name        = "GEMINI_API_KEY"
        secret_name = "gemini-api-key"
      }
      env {
        name        = "ASK_API_KEY"
        secret_name = "ask-api-key"
      }
    }
  }
}

resource "azurerm_container_app" "backend" {
  name                         = local.backend_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"

  secret {
    name  = "jdbc-password"
    value = random_password.postgres.result
  }

  secret {
    name  = "admin-password-hash"
    value = var.admin_password_hash
  }

  secret {
    name  = "jwt-secret"
    value = random_password.jwt_secret.result
  }

  secret {
    name  = "ask-api-key"
    value = random_password.ask_api_key.result
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    # Scale a zero e o equivalente nativo do liga/desliga da AWS (ADR 0026).
    min_replicas = 0
    max_replicas = 1

    container {
      name   = "backend"
      image  = var.backend_image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "azure"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = local.backend_java_opts
      }
      env {
        name  = "JDBC_DATABASE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${azurerm_postgresql_flexible_server_database.estado.name}?sslmode=require"
      }
      env {
        name  = "JDBC_DATABASE_USERNAME"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "JDBC_DATABASE_PASSWORD"
        secret_name = "jdbc-password"
      }
      env {
        name        = "ADMIN_PASSWORD_HASH"
        secret_name = "admin-password-hash"
      }
      env {
        name        = "JWT_SECRET"
        secret_name = "jwt-secret"
      }
      env {
        name        = "ASK_API_KEY"
        secret_name = "ask-api-key"
      }
      env {
        name  = "ASK_API_BASE_URL"
        value = local.ai_agent_url
      }
      env {
        name  = "API_ORIGIN_PERMITIDA"
        value = var.allowed_origins
      }
      env {
        name  = "SPRINGDOC_API_DOCS_ENABLED"
        value = "false"
      }
      env {
        name  = "SPRINGDOC_SWAGGER_UI_ENABLED"
        value = "false"
      }
      env {
        name  = "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"
        value = "4"
      }
      env {
        name  = "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE"
        value = "1"
      }

      # Probes na porta de gerenciamento (profile azure), que o ingress nao
      # expoe: health nunca fica acessivel de fora.
      startup_probe {
        transport               = "HTTP"
        port                    = 8081
        path                    = "/actuator/health"
        interval_seconds        = 5
        failure_count_threshold = 30
      }
      liveness_probe {
        transport = "HTTP"
        port      = 8081
        path      = "/actuator/health"
      }
    }
  }
}
