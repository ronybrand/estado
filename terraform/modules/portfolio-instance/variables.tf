variable "instance_name" {
  description = "Tag Name da instancia"
  type        = string
  default     = "estado-portfolio"
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "ami_id" {
  description = "AMI fixada explicitamente (nao um data source dinamico) - resolver a AMI mais recente a cada apply arriscaria substituir a instancia sem essa ser uma decisao deliberada. Trocar de AMI e uma acao consciente, nao automatica."
  type        = string
}

variable "root_volume_size" {
  type    = number
  default = 30
}

variable "admin_cidr" {
  description = "CIDR autorizado a acessar a porta 22 - o IP do administrador, nunca 0.0.0.0/0"
  type        = string
}

variable "key_name" {
  type    = string
  default = "estado-key"
}

variable "instance_profile_name" {
  description = "Nome do instance profile IAM a anexar (vem do modulo app-backup)"
  type        = string
}
