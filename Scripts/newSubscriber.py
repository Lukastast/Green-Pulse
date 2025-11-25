# subscriber.py
import random
import paho.mqtt.client as mqtt
import json
import ssl
from datetime import datetime

# ---------------- CONFIG (same as your simulator) ---------------- #
BROKER = "localhost"
PORT = 8883
USERNAME = "app_user"
PASSWORD = "appsecure456"
CLIENT_ID = f"terminal-subscriber-{random.randint(1000, 9999)}"

# Topics to listen to (wildcard = everything under greenpulse)
TOPIC = "greenpulse/#"

# ---------------- PRETTY PRINTING ---------------- #
def pretty_print(topic, payload_str):
    try:
        payload = json.loads(payload_str)
        timestamp = datetime.now().strftime("%H:%M:%S")
        
        event = payload.get("event", "raw")
        plant_id = payload.get("plantId", "???")
        
        # Print topic first (in green)
        print(f"\n\033[92m[{timestamp}] ← {topic}\033[0m")
        
        if event == "status":
            print(f"   {plant_id} → Hum: {payload['humidity']}% | "
                  f"pH: {payload['ph']} | Temp: {payload['temperature']}°C | "
                  f"Alive: {'Yes' if payload['alive'] else 'No'}")
        
        elif event == "plant_added":
            print(f"   New plant! ID: {plant_id} | Type: {payload.get('type')} | "
                  f"Env: {payload.get('environment', '?')}")

        elif event == "plant_dead":
            print(f"   R.I.P. {plant_id} has died")

        elif event == "plant_watered":
            print(f"   Watered {plant_id} → New humidity: {payload.get('humidity')}")

        elif event == "ph_adjusted":
            print(f"   pH adjusted for {plant_id} → {payload.get('ph')}")

        elif event == "temp_adjusted":
            print(f"   Temperature set for {plant_id} → {payload.get('temperature')}°C")

        else:
            print(f"   {payload}")

    except json.JSONDecodeError:
        print(f"\n\033[92m[{datetime.now().strftime('%H:%M:%S')}] ← {topic}\033[0m")
        print(f"   Raw: {payload_str}")

# ---------------- MQTT CALLBACKS ---------------- #
def on_connect(client, userdata, flags, reason_code, properties=None):
    if reason_code == 0:
        print(f"Connected to {BROKER}:{PORT}")
        client.subscribe(TOPIC)
        print(f"Subscribed to: {TOPIC}")
        print("Ready! Waiting for plant messages...\n")
    else:
        print(f"Connection failed (code {reason_code})")

def on_message(client, userdata, msg):
    pretty_print(msg.topic, msg.payload.decode())

# ---------------- MAIN ---------------- #
def main():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, CLIENT_ID)
    client.username_pw_set(USERNAME, PASSWORD)
    
    client.tls_set(cert_reqs=ssl.CERT_NONE)
    client.tls_insecure_set(True)
    
    client.on_connect = on_connect
    client.on_message = on_message

    print("Connecting to broker...")
    client.connect(BROKER, PORT, keepalive=60)
    
    try:
        client.loop_forever()
    except KeyboardInterrupt:
        print("\nSubscriber stopped.")
        client.disconnect()

if __name__ == "__main__":
    main()