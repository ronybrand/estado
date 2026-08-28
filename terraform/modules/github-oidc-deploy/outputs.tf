output "role_arn" {
  value = aws_iam_role.deploy.arn
}

# So deve existir UM provider OIDC do GitHub por conta AWS (ver comentario em
# main.tf) - exposto aqui pra outras roles OIDC (ex: modules/github-oidc-plan)
# reusarem em vez de tentar criar um provider novo.
output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}
