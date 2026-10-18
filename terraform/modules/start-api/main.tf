# Liga a EC2 sob demanda (botao "Ligar a API" nos frontends) e a desliga sozinha
# depois de max_uptime_minutes - ver ADR 0026. Stateless: o estado vive em tags
# da propria instancia, sem DynamoDB.

data "archive_file" "lambda" {
  type        = "zip"
  source_file = "${path.module}/lambda/handler.py"
  output_path = "${path.module}/lambda/handler.zip"
}

data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  name               = "estado-start-api"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

resource "aws_iam_role_policy_attachment" "logs" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "ec2" {
  statement {
    actions   = ["ec2:StartInstances", "ec2:StopInstances", "ec2:CreateTags", "ec2:DeleteTags"]
    resources = [var.instance_arn]
  }
  # Describe nao aceita restricao por recurso.
  statement {
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ec2" {
  name   = "ec2-start-stop"
  role   = aws_iam_role.lambda.id
  policy = data.aws_iam_policy_document.ec2.json
}

resource "aws_lambda_function" "start_api" {
  function_name    = "estado-start-api"
  role             = aws_iam_role.lambda.arn
  runtime          = "python3.12"
  handler          = "handler.handler"
  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256
  timeout          = 15
  architectures    = ["arm64"]

  # Sem reserved_concurrent_executions: a conta tem o limite de concorrencia
  # baixo (minimo nao reservado de 10) e a AWS recusa reservar. O teto de abuso
  # fica por conta do Turnstile, do cooldown e do desligamento automatico.

  environment {
    variables = {
      INSTANCE_ID        = var.instance_id
      TURNSTILE_SECRET   = var.turnstile_secret_key
      MAX_UPTIME_MINUTES = tostring(var.max_uptime_minutes)
      COOLDOWN_MINUTES   = tostring(var.cooldown_minutes)
    }
  }
}

resource "aws_lambda_function_url" "start_api" {
  function_name      = aws_lambda_function.start_api.function_name
  authorization_type = "NONE" # protegido pelo desafio Turnstile validado na funcao

  cors {
    allow_origins = var.allowed_origins
    allow_methods = ["POST"]
    allow_headers = ["content-type"]
    max_age       = 3600
  }
}

# Desligamento automatico: a mesma funcao, invocada a cada 15 minutos.
resource "aws_cloudwatch_event_rule" "autostop" {
  name                = "estado-start-api-autostop"
  schedule_expression = "rate(15 minutes)"
}

resource "aws_cloudwatch_event_target" "autostop" {
  rule  = aws_cloudwatch_event_rule.autostop.name
  arn   = aws_lambda_function.start_api.arn
  input = jsonencode({ action = "autostop" })
}

resource "aws_lambda_permission" "autostop" {
  statement_id  = "AllowEventBridgeAutostop"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.start_api.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.autostop.arn
}
