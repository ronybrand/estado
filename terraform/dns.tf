# DNS do dominio proprio (docs/plano-dominio-proprio.md). A zona e criada pelo
# proprio registro do dominio no Route 53 (com os nameservers ja apontados pra
# ela), entao e lida como data source: o Terraform so gerencia os registros e
# nunca consegue apagar a zona.
data "aws_route53_zone" "dominio" {
  name = var.domain_name
}

# TTL baixo de proposito: a troca de IP (ou de instancia) vira uma edicao de
# DNS que propaga em minutos.
#
# Fase 5.3 do plano de migracao (docs/plano-dominio-proprio.md): corte real
# pra instancia nova (us-east-1, t4g.micro/12GB, native arm64 ja validado
# rodando de verdade - ver fase 5.2). A instancia antiga (sa-east-1)
# continua no ar em paralelo, sem nenhum module removido, pra rollback
# imediato (reverter este registro pro IP antigo) se algo der errado.
resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.dominio.zone_id
  name    = "api.${var.domain_name}"
  type    = "A"
  ttl     = 300
  records = [module.portfolio_us_east_1.public_ip]
}

# Frontend Angular no dominio raiz (e no www): o CloudFront serve os dois, e o
# certificado precisa estar no ACM de us-east-1, validado por DNS na mesma zona.
locals {
  frontend_domains = [var.domain_name, "www.${var.domain_name}"]
}

resource "aws_acm_certificate" "frontend" {
  provider                  = aws.us_east_1
  domain_name               = var.domain_name
  subject_alternative_names = ["www.${var.domain_name}"]
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "frontend_cert_validacao" {
  for_each = {
    for o in aws_acm_certificate.frontend.domain_validation_options : o.domain_name => {
      name  = o.resource_record_name
      type  = o.resource_record_type
      value = o.resource_record_value
    }
  }

  zone_id         = data.aws_route53_zone.dominio.zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 60
  records         = [each.value.value]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "frontend" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.frontend.arn
  validation_record_fqdns = [for r in aws_route53_record.frontend_cert_validacao : r.fqdn]
}

resource "aws_route53_record" "frontend" {
  for_each = toset(local.frontend_domains)

  zone_id = data.aws_route53_zone.dominio.zone_id
  name    = each.value
  type    = "A"

  alias {
    name                   = module.estado_frontend.distribution_domain_name
    zone_id                = module.estado_frontend.distribution_hosted_zone_id
    evaluate_target_health = false
  }
}
