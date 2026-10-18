locals {
  backend_name  = "${var.name_prefix}-api"
  ai_agent_name = "${var.name_prefix}-ai-agent"

  # A URL do Cloud Run e previsivel (nome-numero do projeto.regiao.run.app).
  # Montar a partir do nome evita dependencia circular: o backend precisa da URL
  # do agente e o agente, da do backend.
  run_domain   = "${data.google_project.this.number}.${var.region}.run.app"
  backend_url  = "https://${local.backend_name}-${local.run_domain}"
  ai_agent_url = "https://${local.ai_agent_name}-${local.run_domain}"

  ghcr_prefix = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.ghcr.repository_id}"

  # Mesmas opcoes do backend JVM na AWS (deploy/estado/lib-swap.sh), com heap maior.
  backend_java_opts  = "-XX:+UseSerialGC -Xmx384m -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m"
  ai_agent_java_opts = "-XX:+UseSerialGC -Xmx128m -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m"
}

resource "google_service_account" "run" {
  account_id   = "${var.name_prefix}-run"
  display_name = "Container do Cloud Run"

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_iam_member" "run" {
  for_each = toset(["postgres-password", "jwt-secret", "ask-api-key", "admin-password-hash", "gemini-api-key"])

  secret_id = google_secret_manager_secret.main[each.value].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.run.email}"
}

# O agente de servico do Cloud Run precisa ler o repositorio remoto pra puxar
# a imagem.
resource "google_artifact_registry_repository_iam_member" "run_agent" {
  repository = google_artifact_registry_repository.ghcr.name
  location   = var.region
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:service-${data.google_project.this.number}@serverless-robot-prod.iam.gserviceaccount.com"

  depends_on = [google_project_service.apis]
}

resource "google_cloud_run_v2_service" "ai_agent" {
  name     = local.ai_agent_name
  location = var.region

  lifecycle {
    ignore_changes = [scaling]
  }

  # So o backend (BFF) fala com o agente: exposto, quem tivesse a chave poderia
  # forjar X-Forwarded-For e furar o rate limit por IP.
  ingress = "INGRESS_TRAFFIC_INTERNAL_ONLY"

  template {
    service_account = google_service_account.run.email

    scaling {
      min_instance_count = 0
      max_instance_count = 1
    }

    containers {
      image = "${local.ghcr_prefix}/${var.ai_agent_image}"

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }

      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = local.ai_agent_java_opts
      }
      env {
        name  = "ESTADO_API_BASE_URL"
        value = local.backend_url
      }
      env {
        name = "GEMINI_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.main["gemini-api-key"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "ASK_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.main["ask-api-key"].secret_id
            version = "latest"
          }
        }
      }
    }
  }

  depends_on = [
    google_secret_manager_secret_version.main,
    google_secret_manager_secret_iam_member.run,
    google_artifact_registry_repository_iam_member.run_agent,
  ]
}

resource "google_cloud_run_v2_service" "backend" {
  name     = local.backend_name
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  lifecycle {
    ignore_changes = [scaling]
  }

  template {
    service_account = google_service_account.run.email

    scaling {
      # Scale a zero e o equivalente nativo do liga/desliga da AWS (ADR 0026).
      min_instance_count = 0
      max_instance_count = 1
    }

    # Direct VPC egress (sem conector, sem custo fixo): chega no Postgres pelo
    # IP interno. ALL_TRAFFIC porque o agente tem ingress interno, so
    # alcancavel de dentro da VPC (via Private Google Access).
    vpc_access {
      network_interfaces {
        network    = google_compute_network.main.id
        subnetwork = google_compute_subnetwork.main.id
      }
      egress = "ALL_TRAFFIC"
    }

    containers {
      image = "${local.ghcr_prefix}/${var.backend_image}"

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

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
        value = "jdbc:postgresql://${google_compute_address.postgres.address}:5432/estado?sslmode=disable"
      }
      env {
        name  = "JDBC_DATABASE_USERNAME"
        value = "estado"
      }
      env {
        name = "JDBC_DATABASE_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.main["postgres-password"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "ADMIN_PASSWORD_HASH"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.main["admin-password-hash"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.main["jwt-secret"].secret_id
            version = "latest"
          }
        }
      }
      env {
        name = "ASK_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.main["ask-api-key"].secret_id
            version = "latest"
          }
        }
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
    }
  }

  depends_on = [
    google_secret_manager_secret_version.main,
    google_secret_manager_secret_iam_member.run,
    google_artifact_registry_repository_iam_member.run_agent,
    google_compute_instance.postgres,
  ]
}

# O ai-agent so e chamado pelo backend; a protecao e o ingress interno + ASK_API_KEY.
resource "google_cloud_run_v2_service_iam_member" "ai_agent_invoker" {
  name     = google_cloud_run_v2_service.ai_agent.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service_iam_member" "backend_invoker" {
  name     = google_cloud_run_v2_service.backend.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}
