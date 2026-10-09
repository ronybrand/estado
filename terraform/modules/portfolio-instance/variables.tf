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

# Nulo = sem key pair (acesso e so por SSM, como ja e o caso hoje - ver
# DEPLOY_AWS.md). O key pair e sempre regional, entao uma instancia em outra
# regiao (plano em docs/plano-dominio-proprio.md, fase 5) precisaria de um
# novo recurso de qualquer forma; deixar nulo evita recriar esse recurso por
# regiao sem necessidade real.
variable "key_name" {
  type    = string
  default = null
}

# AZ e regiao do alarme eram fixos em "sa-east-1b"/"sa-east-1" dentro deste
# modulo - impedia reusar o modulo numa regiao diferente (plano de migracao
# pra us-east-1/t4g.micro, docs/plano-dominio-proprio.md fase 5).
variable "availability_zone" {
  type    = string
  default = "sa-east-1b"
}

variable "alarm_region" {
  description = "Regiao usada no ARN da acao de auto-recuperacao do alarme (arn:aws:automate:<regiao>:ec2:recover) - deve bater com a regiao do provider usado pra instanciar este modulo."
  type        = string
  default     = "sa-east-1"
}

variable "instance_profile_name" {
  description = "Nome do instance profile IAM a anexar (vem do modulo app-backup)"
  type        = string
}
