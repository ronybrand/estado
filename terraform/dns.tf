# DNS do dominio proprio (docs/plano-dominio-proprio.md). A zona e criada pelo
# proprio registro do dominio no Route 53 (com os nameservers ja apontados pra
# ela), entao e lida como data source: o Terraform so gerencia os registros e
# nunca consegue apagar a zona.
data "aws_route53_zone" "dominio" {
  name = var.domain_name
}

# TTL baixo de proposito: a troca de IP (ou de instancia) vira uma edicao de
# DNS que propaga em minutos.
resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.dominio.zone_id
  name    = "api.${var.domain_name}"
  type    = "A"
  ttl     = 300
  records = [module.portfolio.public_ip]
}
