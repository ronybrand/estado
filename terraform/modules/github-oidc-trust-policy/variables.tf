variable "oidc_provider_arn" {
  description = "ARN do provider OIDC do GitHub ja existente na conta"
  type        = string
}

variable "github_repo" {
  description = "Repo do GitHub autorizado a assumir a role, formato owner/repo"
  type        = string
}

variable "branch" {
  description = "Branch autorizado a assumir a role via sub (e via job_workflow_ref, se workflow_filename for setado)"
  type        = string
  default     = "master"
}

variable "workflow_filename" {
  description = "Nome do arquivo do workflow (em .github/workflows/) autorizado a assumir a role, usado na condicao job_workflow_ref - null (default) nao adiciona essa condicao, deixando a restricao so por branch/repo via sub"
  type        = string
  default     = null
}
