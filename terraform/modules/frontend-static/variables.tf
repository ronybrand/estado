variable "bucket_name" {
  description = "Nome do bucket S3 que guarda o build do Angular - passado explicito, mesmo raciocinio do modules/app-backup (nao arriscar renomear um bucket existente num import)"
  type        = string
}

variable "api_origin_domain" {
  description = "Dominio do backend (Caddy) que recebe o trafego de /api/* - hoje api.<dominio proprio>, ver ADR 0024"
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

variable "aliases" {
  description = "Dominios proprios que apontam pra esta distribuicao (alternate domain names); vazio mantem so o *.cloudfront.net"
  type        = list(string)
  default     = []
}

variable "acm_certificate_arn" {
  description = "ARN do certificado ACM (us-east-1) que cobre os aliases; obrigatorio quando aliases nao e vazio"
  type        = string
  default     = ""
}
