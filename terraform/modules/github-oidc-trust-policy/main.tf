# Shape comum da trust policy OIDC do GitHub Actions, compartilhado entre
# github-oidc-plan e github-oidc-deploy - ver issue #9. Sem essa extracao,
# uma mudanca futura nas condicoes de trust (ex: apertar o padrao do sub,
# adicionar uma claim nova) precisa ser replicada manualmente nos dois
# modulos, sem nenhum check que detecte divergencia.

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

    # Restringe a role a rodar so a partir do branch autorizado deste repo -
    # evita que um PR de fork ou outro branch consiga assumir a role.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/${var.branch}"]
    }

    # Opcional: restringe ao workflow especifico (job_workflow_ref) - sem
    # isso, qualquer workflow deste repo rodando no branch autorizado com
    # "id-token: write" consegue assumir a role. Nem todo consumidor precisa
    # dessa restricao extra (ex: github-oidc-deploy so tem um workflow
    # possivel hoje), por isso e opcional em vez de obrigatoria.
    dynamic "condition" {
      for_each = var.workflow_filename != null ? [var.workflow_filename] : []
      content {
        test     = "StringLike"
        variable = "token.actions.githubusercontent.com:job_workflow_ref"
        values   = ["${var.github_repo}/.github/workflows/${condition.value}@refs/heads/${var.branch}"]
      }
    }
  }
}
