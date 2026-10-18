# Infra do portfolio: uma instancia EC2 compartilhada (modules/portfolio-instance)
# + recursos de backup por app (modules/app-backup, um bloco por app hospedado
# na instancia). Ver docs/adr/ pro raciocinio de cada decisao.

module "estado_backup" {
  source = "./modules/app-backup"

  app_name    = "estado"
  bucket_name = "estado-db-backups-70a63b1a"
}

# Fase 5.4 do plano de migracao (docs/plano-dominio-proprio.md, 2026-10-10):
# a instancia antiga (sa-east-1, t3.micro) foi parada, snapshot final tirado
# (snap-089a592a7663c251a) e removida daqui - o corte de DNS (fase 5.3) e a
# correcao do BACKEND_API_URL do react_estado na Vercel ja confirmaram zero
# trafego real indo pra ela havia mais de 24h. module.portfolio_us_east_1
# abaixo e agora a unica instancia do portfolio.
module "portfolio_us_east_1" {
  source = "./modules/portfolio-instance"
  providers = {
    aws = aws.us_east_1
  }

  instance_name         = "estado-portfolio-us-east-1"
  ami_id                = var.ami_id
  instance_type         = "t4g.micro"
  root_volume_size      = 12
  availability_zone     = "us-east-1a"
  alarm_region          = "us-east-1"
  admin_cidr            = var.admin_cidr
  instance_profile_name = module.estado_backup.instance_profile_name
}

# Frontend Angular estatico (S3 + CloudFront) na frente do mesmo backend
# EC2/Caddy - ver ADR 0013.
module "estado_frontend" {
  source = "./modules/frontend-static"

  bucket_name         = var.frontend_bucket_name
  api_origin_domain   = "api.${var.domain_name}" # ADR 0024; o Caddy atende este nome (deploy/proxy/Caddyfile)
  aliases             = local.frontend_domains
  acm_certificate_arn = aws_acm_certificate_validation.frontend.certificate_arn
  proxy_secret        = var.proxy_secret
}

module "estado_frontend_deploy" {
  source = "./modules/github-oidc-deploy"

  github_repo                 = var.frontend_github_repo
  frontend_bucket_arn         = module.estado_frontend.bucket_arn
  cloudfront_distribution_arn = "arn:aws:cloudfront::${data.aws_caller_identity.current.account_id}:distribution/${module.estado_frontend.distribution_id}"
  # O job de deploy do angular_estado declara `environment: production`
  # (Environment tracking do GitHub) - sem este valor, o sub emitido vira
  # repo:.../environment:production e deixa de bater com o padrao
  # ref:refs/heads/<branch> abaixo, quebrando o AssumeRoleWithWebIdentity.
  environment_name = "production"
}

# Role read-only pro workflow de drift-check do Terraform no CI - ver ADR 0015.
module "estado_terraform_plan" {
  source = "./modules/github-oidc-plan"

  github_repo       = var.backend_github_repo
  oidc_provider_arn = module.estado_frontend_deploy.oidc_provider_arn
  state_bucket_arn  = aws_s3_bucket.terraform_state.arn
}

# Botao "Ligar a API" dos frontends + desligamento automatico - ADR 0026.
module "start_api" {
  source = "./modules/start-api"
  providers = {
    aws = aws.us_east_1
  }

  instance_id          = module.portfolio_us_east_1.instance_id
  instance_arn         = module.portfolio_us_east_1.instance_arn
  turnstile_secret_key = var.turnstile_secret_key
  allowed_origins      = var.start_api_allowed_origins
  turnstile_site_key   = var.turnstile_site_key
  api_ping_url         = "https://api.${var.domain_name}/actuator/info"
  site_url             = "https://${var.domain_name}"
}

# Pra adicionar um novo app de portfolio depois:
#   module "outroapp_backup" {
#     source      = "./modules/app-backup"
#     app_name    = "outroapp"
#     bucket_name = "outroapp-db-backups-<sufixo>"
#   }
# (o instance_profile do outroapp so seria anexado na MESMA instancia se
# fizer sentido reusar a EC2 - normalmente cada app novo do portfolio
# gerencia seu proprio deploy dentro da instancia via Docker, nao precisa de
# instance profile extra na maquina inteira a menos que precise de outro
# recurso AWS proprio.
# ATENCAO: uma instancia EC2 aceita um unico instance profile - anexar dois
# apps que precisem de recursos AWS proprios exige unificar as roles/policies
# num profile so, nao dois modules "app-backup" apontando pro mesmo
# instance_profile_name)
