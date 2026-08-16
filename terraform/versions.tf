terraform {
  required_version = ">= 1.15"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Local por enquanto - projeto de um operador so, numa maquina so. Backend
  # remoto (S3 + locking nativo) e um upgrade natural se isso deixar de ser
  # verdade, nao um requisito desde o dia um (mesmo raciocinio de
  # proporcionalidade das ADRs 0002/0007).
}

provider "aws" {
  region = "sa-east-1"
}
