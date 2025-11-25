import paho.mqtt.client as mqtt
from paho.mqtt.client import CallbackAPIVersion  # Correct import for VERSION2
import uuid  # For unique CLIENT_ID
import ssl  # For TLS

# Configuration
BROKER = "10.42.131.124"  # Private Mosquitto broker
PORT = 8883  # TLS port
USERNAME = "app_user"  # For subscriber
PASSWORD = "appsecure456"  # Password
CLIENT_ID = str(uuid.uuid4())  # Unique ID each run

def on_connect(client, userdata, flags, reason_code, properties):
    print(f"Connected with result code {reason_code}")
    if reason_code == 0:
        print("Subscription successful")
        client.subscribe("greenpulse/newplant", qos=1)  # All data
    else:
        print(f"Connection failed: {reason_code}")

def on_message(client, userdata, msg):
    print(f"Received on {msg.topic}: {msg.payload.decode()}")

# Create client with v2 callback API
client = mqtt.Client(
    callback_api_version=CallbackAPIVersion.VERSION2,
    client_id=CLIENT_ID,
    protocol=mqtt.MQTTv5
)
client.username_pw_set(USERNAME, PASSWORD)  # Auth for private broker
client.tls_set(ca_certs=None, cert_reqs=ssl.CERT_NONE)  # Skip self-signed verification
client.on_connect = on_connect
client.on_message = on_message

client.connect(BROKER, PORT, keepalive=60)
client.loop_forever()