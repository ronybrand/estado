variable "subscription_id" {
  description = "ID da assinatura Azure onde o ambiente e criado"
  type        = string
}

variable "location" {
  description = "Regiao Azure. Se uma assinatura nova der 'location is restricted' no Postgres, troque por outra (ex: eastus2)"
  type        = string
  default     = "brazilsouth"
}

variable "name_prefix" {
  description = "Prefixo dos nomes dos recursos"
  type        = string
  default     = "estado"
}

variable "backend_image" {
  description = "Imagem do backend. JVM de proposito: na imagem native o profile azure nao abre a porta 8081 (condicoes de bean sao resolvidas no build do native)"
  type        = string
  default     = "ghcr.io/ronybrand/estado:jvm-latest"
}

variable "ai_agent_image" {
  description = "Imagem do estado-ai-agent"
  type        = string
  default     = "ghcr.io/ronybrand/estado-ai-agent:latest"
}

variable "allowed_origins" {
  description = "Origens permitidas no CORS do backend (API_ORIGIN_PERMITIDA), separadas por virgula"
  type        = string
}

variable "admin_password_hash" {
  description = "Hash BCrypt da senha do admin (ADMIN_PASSWORD_HASH)"
  type        = string
  sensitive   = true
}

variable "gemini_api_key" {
  description = "Chave da API do Gemini usada pelo estado-ai-agent"
  type        = string
  sensitive   = true
}
