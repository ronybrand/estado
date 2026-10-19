resource "google_compute_network" "main" {
  name                    = "${var.name_prefix}-vpc"
  auto_create_subnetworks = false

  depends_on = [google_project_service.apis]
}

# private_ip_google_access: a VM nao tem IP externo, e e por aqui que ela puxa
# imagens do Artifact Registry e le segredos. O Cloud Run (Direct VPC egress)
# tambem usa IPs desta subnet.
resource "google_compute_subnetwork" "main" {
  name                     = "${var.name_prefix}-subnet"
  region                   = var.region
  network                  = google_compute_network.main.id
  ip_cidr_range            = "10.10.0.0/24"
  private_ip_google_access = true
}

resource "google_compute_firewall" "postgres_from_subnet" {
  name    = "${var.name_prefix}-postgres-internal"
  network = google_compute_network.main.name

  allow {
    protocol = "tcp"
    ports    = ["5432"]
  }

  source_ranges = [google_compute_subnetwork.main.ip_cidr_range]
  target_tags   = ["postgres"]
}

# SSH so pelo tunel IAP (faixa do Google), sem porta 22 exposta na internet.
resource "google_compute_firewall" "ssh_iap" {
  name    = "${var.name_prefix}-ssh-iap"
  network = google_compute_network.main.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["postgres"]
}

