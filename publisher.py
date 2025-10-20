import time
import json
import random
import math
import paho.mqtt.client as mqtt
from datetime import datetime
from paho.mqtt.client import CallbackAPIVersion  # Correct import for VERSION2

# Configuration
BROKER = "localhost"
PORT = 1883
USERNAME = "sensor_user"  # Add for security
PASSWORD = "secureapp123"  # Add for security
CLIENT_ID = "SimulatedPublisher"
ENVIRONMENTS = ["house", "garden", "greenhouse"]
SENSORS = ["light", "humidity", "ph"]

# Optional: Callback for connection status
def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        print("Connected successfully")
    else:
        print(f"Connection failed with reason: {reason_code}")

# Create MQTT client with v2 callback API
client = mqtt.Client(
    callback_api_version=CallbackAPIVersion.VERSION2,
    client_id=CLIENT_ID,
    protocol=mqtt.MQTTv5  # v5 for properties
)
client.username_pw_set(USERNAME, PASSWORD)  # Security
client.on_connect = on_connect  # Optional: For debug

# Connect
client.connect(BROKER, PORT, keepalive=60)
client.loop_start()

# Historical trends (simple dict for demo; use DB in production)
history = {f"plant{i:02d}": {sensor: [] for sensor in SENSORS} for i in range(1, 31)}

try:
    while True:
        for plant_id in range(1, 31):  # 30 plants
            env = random.choice(ENVIRONMENTS)  # Random environment
            for sensor in SENSORS:
                # Daily cycle: Sine wave based on hour (0-23)
                hour = datetime.now().hour
                cycle_factor = math.sin(2 * math.pi * hour / 24)  # -1 to 1

                # Base values per env/sensor + randomization
                if sensor == "light":
                    base = {"house": 500, "garden": 2000, "greenhouse": 1000}[env]
                    value = base + cycle_factor * 200 + random.uniform(-100, 100)
                elif sensor == "humidity":
                    base = {"house": 50, "garden": 70, "greenhouse": 60}[env]
                    value = base + cycle_factor * 10 + random.uniform(-5, 5)
                else:  # pH
                    base = 6.0
                    value = base + random.uniform(-0.5, 0.5)

                # Timestamp
                timestamp = time.time()

                # Append to history
                history_key = f"plant{plant_id:02d}"
                history[history_key][sensor].append(value)
                if len(history[history_key][sensor]) > 5:  # Keep last 5 for trend
                    history[history_key][sensor] = history[history_key][sensor][-5:]

                # Hysteresis/Prediction (only for humidity)
                prediction = None
                if sensor == "humidity":
                    humidity_history = history[history_key]["humidity"]
                    if len(humidity_history) > 0:  # Safe check, though always true after append
                        trend = sum(humidity_history) / len(humidity_history)
                        if trend < 40 and value < trend:
                            prediction = "water in 2 hours"

                # Data payload
                data = {
                    "plantId": f"plant{plant_id:02d}",
                    "environment": env,
                    "sensor": sensor,
                    "value": round(value, 2),
                    "timestamp": timestamp,
                    "prediction": prediction
                }

                # Topic: Hierarchical for scalability
                topic = f"greenpulse/{env}/{data['plantId']}/{sensor}"

                # Publish
                payload = json.dumps(data)
                client.publish(topic, payload, qos=1)
                print(f"📡 Published to {topic}: {payload}")

        # Stats (demo: average humidity across plants, safe for empty lists)
        avg_humidity = sum(
            (sum(h["humidity"]) / len(h["humidity"]) if len(h["humidity"]) > 0 else 0)
            for h in history.values()
        ) / 30
        print(f"📊 Avg Humidity Trend: {round(avg_humidity, 2)}")

        time.sleep(60)  # Simulate slower interval for 90 sensors

except KeyboardInterrupt:
    print("Publisher stopped.")
    client.loop_stop()
    client.disconnect()
