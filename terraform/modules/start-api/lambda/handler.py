"""Liga a EC2 do portfolio sob demanda e a desliga sozinha depois de um tempo.

Tres entradas:
- Function URL GET: pagina com o desafio Turnstile (os frontends so linkam pra ca, entao
  nenhum precisa carregar o widget nem relaxar o proprio CSP).
- Function URL POST ({"token": <Turnstile>}): valida o desafio e liga a instancia.
- EventBridge ({"action": "autostop"}): desliga a instancia que ESTA funcao ligou ha
  mais de MAX_UPTIME_MINUTES. So vale para quem tem a tag AutoStartedAt, entao ligar na
  mao (deploy/aws-power.ps1 on) nunca e desligado aqui.
"""
import json
import os
import time
import urllib.parse
import urllib.request

import boto3

INSTANCE_ID = os.environ["INSTANCE_ID"]
TURNSTILE_SECRET = os.environ["TURNSTILE_SECRET"]
TURNSTILE_SITE_KEY = os.environ["TURNSTILE_SITE_KEY"]
API_PING_URL = os.environ["API_PING_URL"]
SITE_URL = os.environ["SITE_URL"]
MAX_UPTIME_MINUTES = int(os.environ["MAX_UPTIME_MINUTES"])
COOLDOWN_MINUTES = int(os.environ["COOLDOWN_MINUTES"])
STARTED_TAG = "AutoStartedAt"
STOPPED_TAG = "AutoStoppedAt"

ec2 = boto3.client("ec2")


def _instance():
    return ec2.describe_instances(InstanceIds=[INSTANCE_ID])["Reservations"][0]["Instances"][0]


def _tag_epoch(instance, key):
    for tag in instance.get("Tags", []):
        if tag["Key"] == key:
            return int(tag["Value"])
    return None


def _set_tag(key):
    ec2.create_tags(Resources=[INSTANCE_ID], Tags=[{"Key": key, "Value": str(int(time.time()))}])


def _reply(status, body):
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(body),
    }


def _turnstile_ok(token, ip):
    data = urllib.parse.urlencode(
        {"secret": TURNSTILE_SECRET, "response": token, "remoteip": ip}
    ).encode()
    request = urllib.request.Request(
        "https://challenges.cloudflare.com/turnstile/v0/siteverify", data=data
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response).get("success") is True


def _start(event):
    try:
        body = json.loads(event.get("body") or "{}")
    except ValueError:
        return _reply(400, {"message": "invalid json"})
    token = body.get("token") if isinstance(body, dict) else None
    if not isinstance(token, str) or not token:
        return _reply(400, {"message": "missing token"})

    ip = event.get("requestContext", {}).get("http", {}).get("sourceIp", "")
    if not _turnstile_ok(token, ip):
        return _reply(403, {"message": "challenge failed"})

    instance = _instance()
    state = instance["State"]["Name"]
    if state in ("pending", "running"):
        return _reply(200, {"state": "starting" if state == "pending" else "running"})
    if state != "stopped":
        return _reply(409, {"state": state})

    # Evita ciclo ligar/desligar por abuso logo depois de um desligamento automatico.
    stopped_at = _tag_epoch(instance, STOPPED_TAG)
    if stopped_at is not None and time.time() - stopped_at < COOLDOWN_MINUTES * 60:
        return _reply(429, {"state": "cooldown"})

    ec2.start_instances(InstanceIds=[INSTANCE_ID])
    _set_tag(STARTED_TAG)
    return _reply(202, {"state": "starting"})


def _autostop():
    instance = _instance()
    started_at = _tag_epoch(instance, STARTED_TAG)
    if started_at is None or instance["State"]["Name"] != "running":
        return {"action": "none"}

    # Tag velha: a instancia foi religada depois (na mao), entao nao foi uma ligada daqui.
    if instance["LaunchTime"].timestamp() > started_at + 300:
        ec2.delete_tags(Resources=[INSTANCE_ID], Tags=[{"Key": STARTED_TAG}])
        return {"action": "stale-tag-removed"}

    if time.time() - started_at < MAX_UPTIME_MINUTES * 60:
        return {"action": "none"}

    ec2.stop_instances(InstanceIds=[INSTANCE_ID])
    ec2.delete_tags(Resources=[INSTANCE_ID], Tags=[{"Key": STARTED_TAG}])
    _set_tag(STOPPED_TAG)
    return {"action": "stopped"}


PAGE = """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ligar a API</title>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
<style>
body{font-family:system-ui,sans-serif;max-width:32rem;margin:10vh auto;padding:0 1rem;line-height:1.5}
button,a.btn{font:inherit;padding:.6rem 1rem;border:0;border-radius:.4rem;background:#0d6efd;color:#fff;text-decoration:none;display:inline-block}
#status{margin:1rem 0;font-weight:600}
</style></head><body>
<h1>Ligar a API</h1>
<p>A API do projeto Estado fica desligada para economizar custo. Confirme que voce nao e um robo e ela liga sozinha (leva cerca de 1 minuto) e desliga depois de 2 horas.</p>
<p lang="en"><small>The API is switched off to save cost. Verify you are human and it starts by itself (about 1 minute), then stops after 2 hours.</small></p>
<div class="cf-turnstile" data-sitekey="__SITE_KEY__" data-callback="onVerified"></div>
<p id="status" role="status"></p>
<script>
const status = document.getElementById("status");
const started = Date.now();
function say(t){ status.textContent = t; }
function done(){
  say("Pronto! A API respondeu.");
  const a = document.createElement("a");
  a.className = "btn"; a.href = "__SITE_URL__"; a.textContent = "Voltar ao site";
  status.after(a);
}
function ping(){
  const s = Math.round((Date.now() - started) / 1000);
  fetch("__PING_URL__", {mode: "no-cors", cache: "no-store"})
    .then(done)
    .catch(() => { say("Ligando... aguardando a API (" + s + "s)"); setTimeout(ping, 5000); });
}
async function onVerified(token){
  say("Enviando pedido...");
  try {
    const r = await fetch(location.pathname, {method: "POST", headers: {"content-type": "application/json"}, body: JSON.stringify({token})});
    const j = await r.json().catch(() => ({}));
    if (r.status === 429) { say("Aguarde alguns minutos e tente de novo (desligou ha pouco)."); return; }
    if (!r.ok) { say("Nao foi possivel ligar (" + r.status + "). Tente recarregar a pagina."); return; }
    say(j.state === "running" ? "A API ja esta ligada, verificando..." : "Ligando... aguardando a API");
    ping();
  } catch (e) { say("Erro de rede. Tente recarregar a pagina."); }
}
</script></body></html>"""


def _page():
    html = (
        PAGE.replace("__SITE_KEY__", TURNSTILE_SITE_KEY)
        .replace("__PING_URL__", API_PING_URL)
        .replace("__SITE_URL__", SITE_URL)
    )
    return {"statusCode": 200, "headers": {"Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store"}, "body": html}


def handler(event, _context):
    if event.get("action") == "autostop":
        return _autostop()
    if event.get("requestContext", {}).get("http", {}).get("method") == "GET":
        return _page()
    return _start(event)
