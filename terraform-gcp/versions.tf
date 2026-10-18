terraform {
  required_version = ">= 1.15"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
  }

  # State local de proposito: ambiente de teste (sobe, valida, destroi) e o
  # state guarda senhas geradas. O .gitignore deste diretorio impede de ir pro repo.
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone

  # Sem isso as chamadas de budget/billing usariam o projeto do gcloud como
  # "quota project" e falhariam com 403 em contas sem a API ativa la.
  user_project_override = true
  billing_project       = var.project_id
}
