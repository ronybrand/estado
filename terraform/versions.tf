terraform {
  required_version = ">= 1.15"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Backend remoto (S3 + locking nativo, sem DynamoDB) - migrado de state local
  # pra viabilizar drift-check via GitHub Actions. Ver ADR 0015 (contexto:
  # ADR 0011 original considerou isso um "upgrade natural", nao um requisito
  # desde o dia um - deixou de ser hipotetico quando checagem automatizada de
  # drift em CI virou uma necessidade real).
  backend "s3" {
    bucket       = "estado-terraform-state-875304087242"
    key          = "estado/terraform.tfstate"
    region       = "sa-east-1"
    use_lockfile = true
  }
}

provider "aws" {
  region = "sa-east-1"
}
