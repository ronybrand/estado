# Infra do portfolio: uma instancia EC2 compartilhada (modules/portfolio-instance)
# + recursos de backup por app (modules/app-backup, um bloco por app hospedado
# na instancia). Ver docs/adr/ pro raciocinio de cada decisao.

module "estado_backup" {
  source = "./modules/app-backup"

  app_name    = "estado"
  bucket_name = "estado-db-backups-70a63b1a"
}

module "portfolio" {
  source = "./modules/portfolio-instance"

  ami_id                = var.ami_id
  admin_cidr            = var.admin_cidr
  instance_profile_name = module.estado_backup.instance_profile_name
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
