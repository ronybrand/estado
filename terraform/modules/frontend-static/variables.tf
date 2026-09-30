variable "bucket_name" {
  description = "Nome do bucket S3 que guarda o build do Angular - passado explicito, mesmo raciocinio do modules/app-backup (nao arriscar renomear um bucket existente num import)"
  type        = string
}

variable "api_origin_domain" {
  description = "Dominio do backend (Caddy) que recebe o trafego de /api/* - hoje o <elastic-ip>.sslip.io da ADR 0003"
  type        = string
}

variable "proxy_secret" {
  description = "Segredo compartilhado com o backend (RATE_LIMIT_PROXY_SECRET), enviado ao origin /api/* em X-Proxy-Secret pra que o RateLimitFilter aceite o X-Client-IP com o IP real do visitante - ver ADR 0016. Vazio = header nao enviado e o limite continua por IP do no de borda"
  type        = string
  sensitive   = true
  default     = ""
}

variable "price_class" {
  description = "Price class do CloudFront - PriceClass_100 cobre so America do Norte/Europa, suficiente pro publico atual e mais barato que All"
  type        = string
  default     = "PriceClass_100"
}
