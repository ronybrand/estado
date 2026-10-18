terraform {
  required_version = ">= 1.15"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State local de proposito: o ambiente e de teste (sobe, valida, destroi) e o
  # state guarda senhas geradas, entao nao vale um backend remoto que precise
  # existir antes. O .gitignore deste diretorio impede de ir pro repo.
}

provider "azurerm" {
  features {}

  subscription_id = var.subscription_id

  # Os providers ja foram registrados na assinatura (az provider register);
  # sem isso o azurerm tentaria registrar todos e exigiria mais permissao.
  resource_provider_registrations = "none"
}
