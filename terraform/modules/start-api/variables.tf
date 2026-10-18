variable "instance_id" {
  description = "Instancia EC2 que a funcao liga/desliga"
  type        = string
}

variable "instance_arn" {
  description = "ARN da instancia (escopo das permissoes IAM da funcao)"
  type        = string
}

variable "turnstile_secret_key" {
  description = "Secret key do Cloudflare Turnstile (valida o desafio anti-bot no servidor)"
  type        = string
  sensitive   = true
}

variable "allowed_origins" {
  description = "Origens autorizadas a chamar a Function URL (CORS)"
  type        = list(string)
}

variable "max_uptime_minutes" {
  description = "Depois de quanto tempo ligada pela funcao a instancia e desligada sozinha"
  type        = number
  default     = 120
}

variable "cooldown_minutes" {
  description = "Intervalo minimo entre um desligamento automatico e a proxima ligada por visitante"
  type        = number
  default     = 15
}
