variable "admin_cidr" {
  description = "IP do administrador autorizado a SSH (/32) - nunca 0.0.0.0/0, ver ADR 0004"
  type        = string
  sensitive   = true

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0)) && split("/", var.admin_cidr)[1] == "32"
    error_message = "admin_cidr deve ser um IP unico em formato CIDR /32 (ex: 203.0.113.5/32) - nunca uma faixa ampla como 0.0.0.0/0."
  }
}

variable "ami_id" {
  description = "AMI atualmente rodando na instancia - ver comentario em modules/portfolio-instance/variables.tf"
  type        = string
  default     = "ami-064f44895dd6e892a"
}
