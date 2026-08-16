# Instancia unica rodando Docker Compose (app + Postgres) + Caddy, pensada
# pra hospedar varios apps de portfolio na mesma maquina. Ver ADR 0002.

data "aws_vpc" "default" {
  default = true
}

data "aws_subnet" "default_az" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = "sa-east-1b"
  default_for_az    = true
}

data "aws_key_pair" "instance_key" {
  key_name = var.key_name
}

resource "aws_security_group" "portfolio" {
  name = "estado-portfolio-sg"
  # description da AWS e imutavel - mudar o texto forca destroy+recreate do
  # SG em producao. Mantido igual ao valor real ja existente de proposito;
  # o raciocinio de fato (80/443 publico, 22 restrito, 5432 nunca exposto)
  # fica documentado nos comentarios de cada regra abaixo, nao aqui.
  description = "Estado portfolio app"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH - so o IP do administrador"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr]
  }

  ingress {
    description = "HTTP - Lets Encrypt HTTP-01 + redirect do Caddy"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "estado-portfolio-sg"
  }
}

resource "aws_instance" "portfolio" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnet.default_az.id
  vpc_security_group_ids = [aws_security_group.portfolio.id]
  key_name               = data.aws_key_pair.instance_key.key_name
  iam_instance_profile   = var.instance_profile_name

  root_block_device {
    volume_size           = var.root_volume_size
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true

    tags = {
      # Nome herdado do procedimento de troca do ADR 0010 - mantido pra
      # bater com o volume real em vez de forcar uma mudanca cosmetica.
      Name = "estado-portfolio-root-encrypted"
    }
  }

  user_data_replace_on_change = false

  metadata_options {
    http_tokens = "required"
    http_endpoint = "enabled"
    # Nenhum processo containerizado hoje precisa do IMDS (o unico consumidor
    # de credenciais AWS e o backup.sh, que roda no host via systemd, nao em
    # container) - hop 1 basta. O ecs-agent (unico processo que rodava em
    # container e precisaria de hop 2) foi desativado, ver DEPLOY_AWS.md.
    # Ver revisao red-team pos-import do Terraform: hop_limit 2 (herdado do
    # import, nunca declarado aqui) dava a qualquer container - inclusive o
    # da app, exposto a internet - alcance ao IMDS sem necessidade real.
    http_put_response_hop_limit = 1
  }

  tags = {
    Name = var.instance_name
  }

  lifecycle {
    # AMI e volume raiz sao trocados por procedimento deliberado (novo
    # lancamento, ou snapshot+swap como no ADR 0010), nunca por um
    # "terraform apply" recriando a instancia sem essa ser uma decisao
    # explicita.
    ignore_changes = [ami]
  }
}

resource "aws_eip" "portfolio" {
  domain   = "vpc"
  instance = aws_instance.portfolio.id

  tags = {
    Name = "estado-portfolio-eip"
  }
}

resource "aws_cloudwatch_metric_alarm" "ec2_auto_recovery" {
  alarm_name          = "estado-ec2-auto-recovery"
  alarm_description   = "Recupera a instancia automaticamente se o status check de sistema (hardware/hypervisor) falhar"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"

  dimensions = {
    InstanceId = aws_instance.portfolio.id
  }

  alarm_actions = ["arn:aws:automate:sa-east-1:ec2:recover"]
}
