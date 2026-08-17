# Bucket S3 privado (sem website hosting publico) servido via CloudFront com
# Origin Access Control - o bucket nunca fica acessivel direto pela internet,
# so o CloudFront consegue ler dele. Ver ADR 0013.
#
# Sem dominio proprio por enquanto (ADR 0003 ainda vale pro front): a
# distribution usa o dominio default *.cloudfront.net e o certificado default
# do CloudFront. Trocar por dominio proprio depois e so adicionar `aliases` +
# `acm_certificate_arn` no viewer_certificate, sem mudar o bucket nem o app.

resource "aws_s3_bucket" "frontend" {
  bucket = var.bucket_name

  tags = {
    Name = "estado-frontend"
    App  = "estado"
  }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# O deploy.yml faz "aws s3 sync --delete", entao sem versionamento um build
# quebrado sobrescreve/apaga os objetos anteriores sem volta - com isso
# habilitado da pra restaurar a versao anterior de um objeto (ex: via
# "aws s3api copy-object" apontando pro version-id certo) em vez de precisar
# rebuildar um commit antigo. O lifecycle expira versoes antigas apos 30 dias
# pra nao acumular custo de storage indefinidamente.
resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }

  depends_on = [aws_s3_bucket_versioning.frontend]
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "estado-frontend-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  # ExceptHostHeader (nao AllViewer puro): o Caddyfile roteia por virtualhost
  # no dominio sslip.io (ver deploy/proxy/Caddyfile) - se o CloudFront
  # encaminhasse o Host do viewer (dominio *.cloudfront.net), o Caddy nao
  # reconheceria o site e o proxy quebraria. Com esta policy, o CloudFront
  # sobrescreve o Host pro dominio do origin (api_origin_domain) antes de
  # enviar - mantem Caddy funcionando sem mudar o Caddyfile.
  name = "Managed-AllViewerExceptHostHeader"
}

# SPA client-side routing (Angular Router) via CloudFront Function em vez de
# custom_error_response: este ultimo e por distribution, nao por cache
# behavior, entao tambem interceptaria 403/404 legitimos vindos do origin
# api-backend (ex: Spring Security ou um recurso inexistente) e os
# disfarcaria como 200 com o index.html. A function so e associada ao
# default_cache_behavior (origin s3-frontend), entao nunca roda pra /api/*.
resource "aws_cloudfront_function" "spa_fallback" {
  name    = "estado-frontend-spa-fallback"
  runtime = "cloudfront-js-2.0"
  publish = true
  comment = "Reescreve rotas sem extensao de arquivo para /index.html (Angular Router)"
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      var uri = request.uri;
      if (!uri.includes('.')) {
        request.uri = '/index.html';
      }
      return request;
    }
  EOT
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"
  price_class         = var.price_class
  comment             = "estado - frontend Angular (S3) + API (EC2/Caddy)"

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  # /api/* vai direto pro backend atual (EC2 + Caddy) - o Caddyfile ja faz o
  # handle_path que remove o prefixo /api, entao o CloudFront so precisa
  # encaminhar o path como esta.
  origin {
    domain_name = var.api_origin_domain
    origin_id   = "api-backend"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_fallback.arn
    }
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    target_origin_id         = "api-backend"
    viewer_protocol_policy   = "https-only"
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "estado-frontend"
    App  = "estado"
  }
}

data "aws_iam_policy_document" "frontend_bucket_policy" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalRead"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.frontend.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend_bucket_policy.json
}
