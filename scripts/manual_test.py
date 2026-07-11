import uuid
import requests

BASE_URL = "http://localhost:8080"

ACCOUNT_ID = "00000000-0000-0000-0000-000000000001"
DESTINATION_ACCOUNT_ID = "00000000-0000-0000-0000-000000000002"

AMOUNT = 15000.00
DESCRIPTION = "Transferência de teste"

idempotency_key = str(uuid.uuid4())

headers = {
    "Idempotency-Key": idempotency_key,
    "Content-Type": "application/json",

    "X-Device-Id": "python-device-001",
    "X-Geo-Country": "BR",
    "X-Geo-Latitude": "-21.1300",
    "X-Geo-Longitude": "-42.3700",
    "X-Forwarded-For": "177.54.12.34",
}

body = {
    "destinationAccountId": DESTINATION_ACCOUNT_ID,
    "amount": AMOUNT,
    "description": DESCRIPTION,
}

print("Enviando transferência...")
print(f"Idempotency-Key: {idempotency_key}")

response = requests.post(
    f"{BASE_URL}/accounts/{ACCOUNT_ID}/transfers",
    json=body,
    headers=headers,
)

print(f"\nStatus: {response.status_code}")

try:
    data = response.json()
    print("Resposta:")
    print(data)

    transfer_id = data.get("id")
    if transfer_id:
        print("\nConsultando transferência...")

        response = requests.get(
            f"{BASE_URL}/transfers/{transfer_id}"
        )

        print(f"Status GET: {response.status_code}")
        print(response.json())

except Exception:
    print(response.text)