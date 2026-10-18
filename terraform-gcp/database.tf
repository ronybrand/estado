# Postgres numa VM e2-micro (free tier). Sem IP externo: o Google cobra IPv4
# publico e a VM so precisa falar com o Cloud Run (rede interna) e com o
# Artifact Registry/Secret Manager (Private Google Access).

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

locals {
  secrets = {
    postgres-password   = random_password.postgres.result
    jwt-secret          = random_password.jwt_secret.result
    ask-api-key         = random_password.ask_api_key.result
    admin-password-hash = var.admin_password_hash
    gemini-api-key      = var.gemini_api_key
  }
}

resource "google_secret_manager_secret" "main" {
  for_each = nonsensitive(toset(keys(local.secrets)))

  secret_id = "${var.name_prefix}-${each.value}"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "main" {
  for_each = nonsensitive(toset(keys(local.secrets)))

  secret      = google_secret_manager_secret.main[each.value].id
  secret_data = local.secrets[each.value]
}

resource "google_service_account" "postgres_vm" {
  account_id   = "${var.name_prefix}-postgres-vm"
  display_name = "VM do Postgres"

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_iam_member" "postgres_vm" {
  secret_id = google_secret_manager_secret.main["postgres-password"].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.postgres_vm.email}"
}

resource "google_project_iam_member" "postgres_vm_logs" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.postgres_vm.email}"
}

resource "google_artifact_registry_repository_iam_member" "postgres_vm" {
  repository = google_artifact_registry_repository.dockerhub.name
  location   = var.region
  role       = "roles/artifactregistry.reader"
  member     = "serviceAccount:${google_service_account.postgres_vm.email}"
}

resource "google_compute_address" "postgres" {
  name         = "${var.name_prefix}-postgres-ip"
  region       = var.region
  subnetwork   = google_compute_subnetwork.main.id
  address_type = "INTERNAL"
  address      = "10.10.0.10"
}

resource "google_compute_instance" "postgres" {
  name         = "${var.name_prefix}-postgres"
  machine_type = "e2-micro"
  zone         = var.zone
  tags         = ["postgres"]

  boot_disk {
    initialize_params {
      # Container-Optimized OS ja traz Docker; disco padrao de 30 GB e o que o
      # free tier cobre.
      image = "cos-cloud/cos-stable"
      size  = 30
      type  = "pd-standard"
    }
  }

  network_interface {
    subnetwork = google_compute_subnetwork.main.id
    network_ip = google_compute_address.postgres.address
    # Sem access_config: nenhum IP externo.
  }

  service_account {
    email  = google_service_account.postgres_vm.email
    scopes = ["cloud-platform"]
  }

  metadata = {
    startup-script = templatefile("${path.module}/postgres-startup.sh.tftpl", {
      region  = var.region
      project = var.project_id
      secret  = google_secret_manager_secret.main["postgres-password"].secret_id
      image   = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.dockerhub.repository_id}/library/postgres:16-alpine"
    })
  }

  # O disco guarda os dados do banco; trocar a VM por mudanca de metadata nao
  # deve ser a regra.
  allow_stopping_for_update = true

  depends_on = [
    google_secret_manager_secret_version.main,
    google_secret_manager_secret_iam_member.postgres_vm,
    google_artifact_registry_repository_iam_member.postgres_vm,
  ]
}
