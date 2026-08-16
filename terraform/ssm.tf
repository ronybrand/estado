# Acesso via AWS Systems Manager Session Manager, no lugar de depender so de
# SSH restrito por IP (admin_cidr). Motivado por um problema real: o
# administrador nao tem IP fixo (internet residencial variavel + as vezes
# roteando pelo celular, onde a operadora bloqueia a porta 22 de saida) - toda
# troca de rede exigia atualizar admin_cidr e reaplicar o Terraform so pra
# conseguir conectar.
#
# SSM tunela via API da AWS sobre HTTPS (porta 443), autenticado por IAM em
# vez de IP de origem - nao depende de rede, so de estar logado na AWS (o que
# torna o MFA do rony-admin, ja recomendado numa revisao anterior, uma
# protecao real desse caminho de acesso, diferente de SSH+chave). SSH com
# admin_cidr continua existindo, nao foi removido - SSM e um caminho a mais,
# nao substitui a regra do Security Group.
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = module.estado_backup.role_name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
