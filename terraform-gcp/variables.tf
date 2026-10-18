variable "project_id" {
  description = "ID do projeto GCP dedicado a este ambiente (apagar o projeto leva tudo junto)"
  type        = string
}

variable "billing_account_id" {
  description = "Conta de faturamento vinculada ao projeto, formato XXXXXX-XXXXXX-XXXXXX"
  type        = string
}

variable "region" {
  description = "Regiao. O free tier da e2-micro so existe em us-west1, us-central1 e us-east1"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona da VM do Postgres (dentro da regiao acima)"
  type        = string
  default     = "us-central1-a"
}

variable "name_prefix" {
  description = "Prefixo dos nomes dos recursos"
  type        = string
  default     = "estado"
}

variable "budget_amount_brl" {
  description = "Teto mensal em reais (a conta de faturamento e em BRL; ~US$ 5). Ao atingi-lo o faturamento do projeto e desvinculado"
  type        = number
  default     = 30
}

variable "kill_switch_dry_run" {
  description = "true: a funcao so registra no log que desvincularia o faturamento, sem desvincular"
  type        = bool
  default     = false
}
