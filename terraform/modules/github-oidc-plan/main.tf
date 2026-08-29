# Permite ao GitHub Actions rodar "terraform plan" (drift-check agendado) sem
# access key estatica na conta - o workflow assume esta role via OIDC, com
# token de vida curta emitido pelo proprio GitHub. Ver ADR 0015.
#
# So-leitura: a unica acao de mutacao permitida e PutObject/DeleteObject no
# lockfile nativo do S3, dentro do bucket de state - nao ha escrita sobre o
# terraform.tfstate em si nem sobre o resto do bucket. Reusa o provider OIDC
# ja criado em modules/github-oidc-deploy (var.oidc_provider_arn) - nao cria
# um provider novo.

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

    # Restringe ao workflow especifico de drift-check - sem isso, qualquer
    # outro workflow deste repo rodando em master que ganhe "id-token: write"
    # (hoje nenhum tem, mas nada impede que passe a ter) tambem conseguiria
    # assumir esta role.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:job_workflow_ref"
      values   = ["${var.github_repo}/.github/workflows/${var.workflow_filename}@refs/heads/master"]
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

# "terraform plan" precisa ler o state inteiro e listar o bucket, mas nao
# precisa escrever nele - a unica escrita legitima de uma role de plan e o
# lockfile nativo do S3 (use_lockfile), tratado na statement seguinte.
data "aws_iam_policy_document" "state_bucket_access" {
  statement {
    sid       = "TerraformStateRead"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:ListBucket"]
    resources = [var.state_bucket_arn, "${var.state_bucket_arn}/*"]
  }

  # Excecao de escrita, restrita ao lockfile: o locking nativo do S3
  # (use_lockfile) escreve um lockfile temporario (key + ".tflock") mesmo
  # durante um "plan" - sem isso, o job falharia tentando adquirir o lock.
  # DeleteObject e necessario tambem - sem ele, o Terraform adquire o lock
  # mas nao consegue remove-lo ao final, deixando um lockfile orfao que
  # bloqueia toda execucao seguinte. Restrito ao path do lockfile (nao ao
  # bucket inteiro) pra essa role nao conseguir sobrescrever/apagar o
  # terraform.tfstate em si.
  statement {
    sid       = "TerraformStateLockfileWrite"
    effect    = "Allow"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${var.state_bucket_arn}/${var.state_key}.tflock"]
  }
}

resource "aws_iam_role_policy" "state_bucket_access" {
  name   = "${var.role_name}-state-bucket"
  role   = aws_iam_role.plan.id
  policy = data.aws_iam_policy_document.state_bucket_access.json
}
