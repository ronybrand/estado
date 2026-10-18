output "backend_url" {
  description = "URL publica do backend (acorda do scale a zero na primeira requisicao)"
  value       = local.backend_url
}

output "postgres_internal_ip" {
  description = "IP interno do Postgres (so alcancavel de dentro da VPC)"
  value       = google_compute_address.postgres.address
}

output "ssh_postgres" {
  description = "Acesso a VM do Postgres pelo tunel IAP"
  value       = "gcloud compute ssh ${google_compute_instance.postgres.name} --zone ${var.zone} --tunnel-through-iap --project ${var.project_id}"
}
