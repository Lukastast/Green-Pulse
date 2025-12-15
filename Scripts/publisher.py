import json
import random
import time
import threading
import ssl
import uuid
import logging
from datetime import datetime

import paho.mqtt.client as mqtt
import firebase_admin
from firebase_admin import credentials, firestore

# ---------------- MQTT CONFIG ---------------- #
BROKER = "host.docker.internal"  # docker localhost
PORT = 8883
USERNAME = "sensor_user" 
PASSWORD = "securepass123"#securepass123 appsecure456
CLIENT_ID = f"greenpulse-sim-{uuid.uuid4()}"
TOPIC_SIM = "greenpulse/simulator"
TOPIC_NEW_PLANT = "greenpulse/newplant"
TOPIC_WATER = "greenpulse/water"
KEEPALIVE = 60

# ---------------- FIRESTORE SETUP ---------------- #
cred = credentials.Certificate("serviceAccountKey2.json")
firebase_admin.initialize_app(cred)
db = firestore.client()

# ---------------- PLANT LOGIC ---------------- #
active_plants = {}
update_interval = 15  # seconds

plant_types = {
    "cactus": {"ideal_ph": 6.0, "min_hum": 20, "max_hum": 40, "dry_rate": 0.5},
    "tropical": {"ideal_ph": 6.5, "min_hum": 60, "max_hum": 90, "dry_rate": 0.2},
    "herb": {"ideal_ph": 6.8, "min_hum": 50, "max_hum": 80, "dry_rate": 0.3},
    "default": {"ideal_ph": 6.5, "min_hum": 40, "max_hum": 70, "dry_rate": 0.3}
}

MAX_BAD_HOURS = 12

# ---------------- FIRESTORE LOADER ---------------- #
def load_plants_from_firestore():
    global active_plants
    active_plants.clear()
    print("Loading ALL plants from Firestore (admin mode)...")

    # This gets plants from EVERY user
    plants_ref = db.collection_group("plants")
    docs = plants_ref.stream()

    total = 0
    for doc in docs:
        data = doc.to_dict()
        plant_id = doc.id

        # Extract environment from path: users/uid/environments/Outdoors/plants/id
        path_parts = doc.reference.path.split("/")
        if len(path_parts) >= 5:
            environment = path_parts[5]  # "Outdoors", etc.
        else:
            environment = "Unknown"

        plant_type = data.get("type", "herb")
        env_config = plant_types.get(plant_type, plant_types["default"])

        active_plants[plant_id] = {
            "type": plant_type,
            "environment": environment,
            "humidity": data.get("humidity", random.uniform(env_config["min_hum"], env_config["max_hum"])),
            "ph": data.get("ph", random.uniform(6.0, 7.5)),
            "temperature": data.get("temperature", random.uniform(18, 28)),
            "bad_hours": 0,
            "alive": data.get("alive", True)
        }
        total += 1
        print(f"Loaded {plant_id} ({plant_type}) in {environment}")

    print(f"Loaded {total} plants from ALL users")

def on_snapshot(col_snapshot, changes, read_time):
    print("Firestore plants changed — reloading...")
    load_plants_from_firestore()

# ---------------- PLANT LOGIC ---------------- #

def water_plant(plant_id, amount):
    if plant_id in active_plants:
        plant = active_plants[plant_id]
        plant["humidity"] += amount * 100
        if plant["humidity"] > 100:
            plant["humidity"] = 100
        print(f"💧 Watered {plant_id}. New humidity: {plant['humidity']:.1f}%")

def regulate_ph(plant_id, target):
    if plant_id in active_plants:
        plant = active_plants[plant_id]
        delta = target - plant["ph"]
        plant["ph"] += delta * 0.5
        print(f"⚗️ Regulated {plant_id} pH to {plant['ph']:.2f}")

# ---------------- SIMULATION LOOP ---------------- #
def update_plants(client):
    while True:
        if not active_plants:
            time.sleep(1)
            continue

        for plant_id, plant in list(active_plants.items()):
            if not plant["alive"]:
               continue

            env = plant_types.get(plant["type"], plant_types["default"])
            temp_effect = (plant["temperature"] - 20) * 0.02
            humidity_loss = (env["dry_rate"] + temp_effect) * random.uniform(0.8, 1.2)
            plant["humidity"] -= humidity_loss
            plant["humidity"] = max(0, plant["humidity"])

            plant["ph"] += random.uniform(-0.05, 0.05)
            plant["ph"] = max(4.0, min(8.0, plant["ph"]))

            bad = (
                plant["humidity"] < env["min_hum"] or
                plant["humidity"] > env["max_hum"] or
                abs(plant["ph"] - env["ideal_ph"]) > 1.0
            )

            if bad:
                plant["bad_hours"] += 1
                if plant["bad_hours"] >= MAX_BAD_HOURS:
                    plant["alive"] = False
                    client.publish(TOPIC_SIM, json.dumps({
                        "event": "plant_dead",
                        "plantId": plant_id
                    }))
                    print(f"Dead: {plant_id}")
            else:
                plant["bad_hours"] = 0

            # Publish status
            payload = json.dumps({
                "event": "status",
                "plantId": plant_id,
                "humidity": round(plant["humidity"], 1),
                "ph": round(plant["ph"], 2),
                "temperature": round(plant["temperature"], 1),
                "alive": plant["alive"]
            })

            client.publish(TOPIC_SIM, payload, qos=1)

        time.sleep(update_interval)

# ---------------- MQTT HANDLERS ---------------- #

def on_connect(client, userdata, flags, reason_code, properties=None):
    print(f"Connected with code {reason_code}")
    if reason_code == 0:
        # Subscribe to all command topics
        client.subscribe("greenpulse/newplant")      # New plant creation
        client.subscribe("greenpulse/commands/water")   # Water command
        client.subscribe("greenpulse/commands/ph")      # pH regulation
        client.subscribe("greenpulse/commands/temp")    # Temperature set
        client.subscribe("greenpulse/simulator")        # Optional: status feedback

        print("Subscribed to all command topics")
        load_plants_from_firestore()  # Load real plants
    else:
        print("Connection failed")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        plant_id = payload.get("plantId")
        action = payload.get("action")

        if not plant_id:
            print(f"Missing plantId in message on {msg.topic}")
            return

        # Route based on topic OR action — both work
        topic = msg.topic

        print(f"MQTT ← {topic}: {payload}")

        # === NEW PLANT CREATION ===
        if topic == "greenpulse/newplant" or action == "add_plant":
            if plant_id not in active_plants:
                plant_type = payload.get("type", "herb")
                environment = payload.get("environment", "Indoors")
                env_config = plant_types.get(plant_type, plant_types["default"])

                active_plants[plant_id] = {
                    "type": plant_type,
                    "environment": environment,
                    "humidity": random.uniform(env_config["min_hum"], env_config["max_hum"]),
                    "ph": random.uniform(6.0, 7.5),
                    "temperature": random.uniform(18, 28),
                    "bad_hours": 0,
                    "alive": True
                }
                print(f"Added new plant: {plant_id} ({plant_type}) in {environment}")
                client.publish(TOPIC_SIM, json.dumps({
                    "event": "plant_added",
                    "plantId": plant_id,
                    "type": plant_type,
                    "environment": environment
                }))
            return

        # === WATER COMMAND ===
        if topic.endswith("/water") or action == "water":
            amount = float(payload.get("amount", 0.3))
            water_plant(plant_id, amount)
            if plant_id in active_plants:
                client.publish(TOPIC_SIM, json.dumps({
                    "event": "plant_watered",
                    "plantId": plant_id,
                    "humidity": round(active_plants[plant_id]["humidity"], 1)
                }))
            return

        # === PH COMMAND ===
        if topic.endswith("/ph") or action == "set_ph":
            target = float(payload.get("target", 6.5))
            regulate_ph(plant_id, target)
            if plant_id in active_plants:
                client.publish(TOPIC_SIM, json.dumps({
                    "event": "ph_adjusted",
                    "plantId": plant_id,
                    "ph": round(active_plants[plant_id]["ph"], 2)
                }))
            return

        # === TEMP COMMAND ===
        if topic.endswith("/temp") or action == "set_temp":
            temp = float(payload.get("temp", 22.0))
            if plant_id in active_plants:
                active_plants[plant_id]["temperature"] = temp
                print(f"Temperature set for {plant_id}: {temp}°C")
                client.publish(TOPIC_SIM, json.dumps({
                    "event": "temp_adjusted",
                    "plantId": plant_id,
                    "temperature": temp
                }))
            return

        print(f"Ignored unknown / own message on {topic}")

    except Exception as e:
        print(f"Error in on_message: {e}")

# ---------------- MAIN ---------------- #

def main():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, CLIENT_ID)
    client.username_pw_set(USERNAME, PASSWORD)  # Auth for private broker
    client.on_connect = on_connect
    client.on_message = on_message

    client.tls_set(cert_reqs=ssl.CERT_NONE)  # Skip self-signed verification
    client.connect(BROKER, PORT, KEEPALIVE)
    client.loop_start()

    # Start background simulation for ongoing bursts
    threading.Thread(target=update_plants, args=(client,), daemon=True).start()

    # Keep running
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Simulator stopped.")
        client.loop_stop()
        client.disconnect()

if __name__ == "__main__":
    main()