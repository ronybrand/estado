# Permite ao GitHub Actions publicar o build do Angular no S3 e invalidar o
# CloudFront sem access key estatica na conta - o workflow assume esta role
# via OIDC, com token de vida curta emitido pelo proprio GitHub. Ver ADR 0013.
#
# So deve existir UM provider OIDC do GitHub por conta AWS - se a conta ja
# tiver um (outro app do portfolio, por exemplo), este resource precisa ser
# importado em vez de criado de novo.

resource "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"

  client_id_list = ["sts.amazonaws.com"]

  # Thumbprints publicados pelo GitHub para os root CAs em uso - ver
  # https://docs.github.com/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
  ]
}

# Trust policy compartilhada com modules/github-oidc-plan - ver issue #9.
# Sem workflow_filename: restringe so por repo/branch (via sub), evitando que
# um PR de fork ou outro branch consiga publicar no S3.
module "trust_policy" {
  source = "../github-oidc-trust-policy"

  oidc_provider_arn = aws_iam_openid_connect_provider.github.arn
  github_repo       = var.github_repo
}

resource "aws_iam_role" "deploy" {
  name               = var.role_name
  assume_role_policy = module.trust_policy.json
}

data "aws_iam_policy_document" "deploy_permissions" {
  statement {
    sid     = "SyncBucket"
    effect  = "Allow"
    actions = ["s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [
      var.frontend_bucket_arn,
      "${var.frontend_bucket_arn}/*",
    ]
  }

  statement {
    sid       = "InvalidateDistribution"
    effect    = "Allow"
    actions   = ["cloudfront:CreateInvalidation"]
    resources = [var.cloudfront_distribution_arn]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "${var.role_name}-permissions"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy_permissions.json
}
