# Permite ao GitHub Actions rodar "terraform plan" (drift-check agendado) sem
# access key estatica na conta - o workflow assume esta role via OIDC, com
# token de vida curta emitido pelo proprio GitHub. Ver ADR 0015.
#
# So-leitura: nenhuma acao de mutacao e permitida fora do bucket de state (que
# precisa de escrita pro lockfile nativo do S3). Reusa o provider OIDC ja
# criado em modules/github-oidc-deploy (var.oidc_provider_arn) - nao cria um
# provider novo.

data "aws_iam_policy_document" "assume_role_github" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Restringe a role a rodar so a partir do branch master deste repo - evita
    # que um PR de fork ou outro branch consiga assumir a role.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/master"]
    }
  }
}

resource "aws_iam_role" "plan" {
  name               = var.role_name
  assume_role_policy = data.aws_iam_policy_document.assume_role_github.json
}

# Politica gerenciada da AWS, nao uma politica customizada granular: "terraform
# plan" precisa de Describe/Get/List em praticamente todo servico usado neste
# projeto (EC2, IAM, S3, CloudWatch, CloudFront, CloudTrail), e essa lista
# cresce a cada recurso novo adicionado ao .tf. Uma politica customizada
# exigiria manutencao continua so pra acompanhar leituras, sem reduzir o
# blast radius real - a role ja e so-leitura, sem nenhuma permissao de escrita
# fora do bucket de state abaixo. Ver ADR 0015, alternativas consideradas.
resource "aws_iam_role_policy_attachment" "read_only" {
  role       = aws_iam_role.plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# Excecao de escrita: o locking nativo do S3 (use_lockfile) escreve um
# lockfile temporario no bucket de state mesmo durante um "plan" - sem isso, o
# job falharia tentando adquirir o lock. DeleteObject e necessario tambem -
# sem ele, o Terraform adquire o lock mas nao consegue remove-lo ao final,
# deixando um lockfile orfao que bloqueia toda execucao seguinte.
data "aws_iam_policy_document" "state_bucket_access" {
  statement {
    sid       = "TerraformStateReadWrite"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [var.state_bucket_arn, "${var.state_bucket_arn}/*"]
  }
}

resource "aws_iam_role_policy" "state_bucket_access" {
  name   = "${var.role_name}-state-bucket"
  role   = aws_iam_role.plan.id
  policy = data.aws_iam_policy_document.state_bucket_access.json
}
