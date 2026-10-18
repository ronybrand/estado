import base64
import json
import os

import functions_framework
from googleapiclient import discovery

PROJECT_ID = os.environ["PROJECT_ID"]
DRY_RUN = os.environ.get("DRY_RUN", "false").lower() == "true"


@functions_framework.cloud_event
def stop_billing(cloud_event):
    message = json.loads(base64.b64decode(cloud_event.data["message"]["data"]))
    cost = message["costAmount"]
    budget = message["budgetAmount"]

    # O orcamento notifica a cada atualizacao de custo, nao so ao estourar.
    if cost < budget:
        print(f"Gasto {cost} abaixo do teto {budget}, nada a fazer.")
        return

    billing = discovery.build("cloudbilling", "v1", cache_discovery=False)
    name = f"projects/{PROJECT_ID}"

    if not billing.projects().getBillingInfo(name=name).execute().get("billingEnabled"):
        print("Faturamento ja desvinculado.")
        return

    if DRY_RUN:
        print(f"DRY_RUN: gasto {cost} >= teto {budget}, desvincularia o faturamento de {name}.")
        return

    billing.projects().updateBillingInfo(name=name, body={"billingAccountName": ""}).execute()
    print(f"Gasto {cost} >= teto {budget}: faturamento de {name} desvinculado.")
