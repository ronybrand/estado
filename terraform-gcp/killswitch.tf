# Corte automatico de gasto. O GCP nao tem "so o free tier" nem teto de gasto:
# um orcamento apenas avisa. Aqui o orcamento publica num topico Pub/Sub e uma
# funcao desvincula o faturamento do projeto quando o gasto passa do teto, o
# que para os recursos. A medicao de custo atrasa algumas horas, entao o corte
# pode vir um pouco depois do limite.

data "google_project" "this" {
  project_id = var.project_id
}

locals {
  apis = [
    "billingbudgets.googleapis.com",
    "cloudbilling.googleapis.com",
    "pubsub.googleapis.com",
    "cloudfunctions.googleapis.com",
    "cloudbuild.googleapis.com",
    "run.googleapis.com",
    "eventarc.googleapis.com",
    "artifactregistry.googleapis.com",
    "storage.googleapis.com",
    "logging.googleapis.com",
    "iam.googleapis.com",
    "compute.googleapis.com",
    "secretmanager.googleapis.com",
    "dns.googleapis.com",
  ]
}

resource "google_project_service" "apis" {
  for_each = toset(local.apis)

  service            = each.value
  disable_on_destroy = false
}

resource "google_pubsub_topic" "budget" {
  name       = "${var.name_prefix}-budget-alerts"
  depends_on = [google_project_service.apis]
}

resource "google_billing_budget" "main" {
  billing_account = var.billing_account_id
  display_name    = "${var.name_prefix}-teste-${var.budget_amount_brl}brl"

  budget_filter {
    projects = ["projects/${data.google_project.this.number}"]
  }

  amount {
    specified_amount {
      currency_code = "BRL"
      units         = tostring(var.budget_amount_brl)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.8
  }
  threshold_rules {
    threshold_percent = 1.0
  }
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  all_updates_rule {
    pubsub_topic   = google_pubsub_topic.budget.id
    schema_version = "1.0"
  }

  depends_on = [google_project_service.apis]
}

resource "google_service_account" "kill_switch" {
  account_id   = "${var.name_prefix}-kill-switch"
  display_name = "Desvincula o faturamento ao estourar o orcamento"

  depends_on = [google_project_service.apis]
}

# Desvincular um projeto exige permissao na conta de faturamento. Ela e
# compartilhada com outros projetos, entao a funcao so age em var.project_id
# (codigo em functions/stop-billing).
resource "google_billing_account_iam_member" "kill_switch" {
  billing_account_id = var.billing_account_id
  role               = "roles/billing.admin"
  member             = "serviceAccount:${google_service_account.kill_switch.email}"
}

resource "google_project_iam_member" "kill_switch_project_billing" {
  project = var.project_id
  role    = "roles/billing.projectManager"
  member  = "serviceAccount:${google_service_account.kill_switch.email}"
}

# getBillingInfo exige ler o projeto; sem isso a funcao toma 403.
resource "google_project_iam_member" "kill_switch_project_read" {
  project = var.project_id
  role    = "roles/browser"
  member  = "serviceAccount:${google_service_account.kill_switch.email}"
}

# Contas novas nao dao mais permissoes padrao a service account do Compute, e o
# build da funcao roda com ela.
resource "google_project_iam_member" "build_permissions" {
  for_each = toset([
    "roles/cloudbuild.builds.builder",
    "roles/artifactregistry.writer",
    "roles/logging.logWriter",
    "roles/storage.objectViewer",
  ])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${data.google_project.this.number}-compute@developer.gserviceaccount.com"

  # A service account padrao so existe depois que a API do Compute e ativada.
  depends_on = [google_project_service.apis]
}

data "archive_file" "kill_switch" {
  type        = "zip"
  source_dir  = "${path.module}/functions/stop-billing"
  output_path = "${path.module}/.build/stop-billing.zip"
}

resource "google_storage_bucket" "functions_source" {
  name                        = "${var.project_id}-functions-src"
  location                    = var.region
  uniform_bucket_level_access = true
  force_destroy               = true

  depends_on = [google_project_service.apis]
}

resource "google_storage_bucket_object" "kill_switch" {
  name   = "stop-billing-${data.archive_file.kill_switch.output_md5}.zip"
  bucket = google_storage_bucket.functions_source.name
  source = data.archive_file.kill_switch.output_path
}

resource "google_cloudfunctions2_function" "kill_switch" {
  name     = "${var.name_prefix}-stop-billing"
  location = var.region

  build_config {
    runtime     = "python312"
    entry_point = "stop_billing"
    source {
      storage_source {
        bucket = google_storage_bucket.functions_source.name
        object = google_storage_bucket_object.kill_switch.name
      }
    }
  }

  service_config {
    max_instance_count    = 1
    available_memory      = "256M"
    timeout_seconds       = 60
    service_account_email = google_service_account.kill_switch.email

    environment_variables = {
      PROJECT_ID = var.project_id
      DRY_RUN    = tostring(var.kill_switch_dry_run)
    }
  }

  event_trigger {
    trigger_region = var.region
    event_type     = "google.cloud.pubsub.topic.v1.messagePublished"
    pubsub_topic   = google_pubsub_topic.budget.id
    retry_policy   = "RETRY_POLICY_DO_NOT_RETRY"
  }

  depends_on = [
    google_project_service.apis,
    google_project_iam_member.build_permissions,
    google_billing_account_iam_member.kill_switch,
  ]
}
