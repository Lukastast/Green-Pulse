import time
import json
import random
import paho.mqtt.client as mqtt

# Configuration
BROKER = "localhost"          # or the IP of the Mosquitto broker
PORT = 1883                   # 8883 if using TLS
TOPIC = "sensor/data"         # can be anything
CLIENT_ID = "SimulatedPublisher"

# Create MQTT client
client = mqtt.Client(CLIENT_ID)

# Connect to the broker
client.connect(BROKER, PORT, keepalive=60)
client.loop_start()

try:
    while True:
        # Simulate data (example: temperature + humidity)
        simulated_data = {
            "deviceId": "sensor-001",
            "temperature": round(random.uniform(18.0, 25.0), 2),
            "humidity": round(random.uniform(30.0, 60.0), 2),
            "timestamp": time.time()
        }

        # Convert to JSON string
        payload = json.dumps(simulated_data)

        # Publish to the topic
        client.publish(TOPIC, payload, qos=1)
        print(f"📡 Published: {payload}")

        # Wait between messages
        time.sleep(2)

except KeyboardInterrupt:
    print("Publisher stopped.")
    client.loop_stop()
    client.disconnect()
