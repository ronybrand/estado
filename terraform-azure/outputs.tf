output "backend_url" {
  description = "URL publica do backend (acorda do scale a zero na primeira requisicao)"
  value       = local.backend_url
}

output "postgres_fqdn" {
  description = "FQDN do Postgres (endpoint publico, protegido por TLS e senha)"
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "resource_group" {
  description = "Resource group; 'az group delete' remove tudo de uma vez"
  value       = azurerm_resource_group.main.name
}
