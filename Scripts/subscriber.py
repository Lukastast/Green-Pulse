import paho.mqtt.client as mqtt
from paho.mqtt.client import CallbackAPIVersion
import uuid
import ssl

BROKER = "localhost"
PORT = 8883
USERNAME = "app_user"
PASSWORD = "appsecure456"
CLIENT_ID = str(uuid.uuid4())

def on_connect(client, userdata, flags, reason_code, properties):
    print(f"Connected with result code {reason_code}")
    if reason_code == 0:
        print("Subscription successful")
        client.subscribe("greenpulse/newplant", qos=1)  # All data
    else:
        print(f"❌ Connection failed: {reason_code}")

def on_message(client, userdata, msg):
    print(f"📨 Received on {msg.topic}: {msg.payload.decode()}")

client = mqtt.Client(
    callback_api_version=CallbackAPIVersion.VERSION2,
    client_id=CLIENT_ID,
    protocol=mqtt.MQTTv5
)
client.username_pw_set(USERNAME, PASSWORD)
client.tls_set(cert_reqs=ssl.CERT_NONE)
client.tls_insecure_set(True)  # Allow self-signed certificates

client.on_connect = on_connect
client.on_message = on_message

print(f"🔌 Connecting to {BROKER}:{PORT}...")
client.connect(BROKER, PORT, keepalive=60)
client.loop_forever()