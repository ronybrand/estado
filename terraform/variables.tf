variable "admin_cidr" {
  description = "IP do administrador autorizado a SSH (/32) - nunca 0.0.0.0/0, ver ADR 0004"
  type        = string
  sensitive   = true
}

variable "ami_id" {
  description = "AMI atualmente rodando na instancia - ver comentario em modules/portfolio-instance/variables.tf"
  type        = string
  default     = "ami-064f44895dd6e892a"
}
