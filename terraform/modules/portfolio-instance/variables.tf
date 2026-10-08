variable "instance_name" {
  description = "Tag Name da instancia"
  type        = string
  default     = "estado-portfolio"
}

# t3.micro (1 GB): cabe com o backend em native image (ADR 0022). O pico de memoria
# fica no swap do agent (duas JVMs ao mesmo tempo, ~650 MB medidos), com a swap de
# 2 GB da instancia como folga. Voltar pra t3.small se a swap passar a ser usada
# de forma sustentada.
variable "instance_type" {
  type    = string
  default = "t3.micro"
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
