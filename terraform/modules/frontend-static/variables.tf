variable "bucket_name" {
  description = "Nome do bucket S3 que guarda o build do Angular - passado explicito, mesmo raciocinio do modules/app-backup (nao arriscar renomear um bucket existente num import)"
  type        = string
}

variable "api_origin_domain" {
  description = "Dominio do backend (Caddy) que recebe o trafego de /api/* - hoje o <elastic-ip>.sslip.io da ADR 0003"
  type        = string
}

variable "price_class" {
  description = "Price class do CloudFront - PriceClass_100 cobre so America do Norte/Europa, suficiente pro publico atual e mais barato que All"
  type        = string
  default     = "PriceClass_100"
}
