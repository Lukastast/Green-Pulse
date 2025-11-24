import json
import random
import time
import threading
import ssl
import uuid
import paho.mqtt.client as mqtt
from datetime import datetime  # For timestamps

# ---------------- MQTT CONFIG ---------------- #
BROKER = "10.42.131.124"  # Private EMQX broker
PORT = 8883
USERNAME = "sensor_user"  # Use sensor_user for full publish access to greenpulse/#
PASSWORD = "securepass123"  # Replace with your actual password for sensor_user
CLIENT_ID = f"greenpulse-sim-{uuid.uuid4()}"
KEEPALIVE = 60
TOPIC = "greenpulse/simulator"  # Main topic for events and sensor data

# ---------------- PLANT DATA ---------------- #
active_plants = {}
update_interval = 15  # 15 seconds for bursts every 15s

# How long (in simulated hours) a plant can survive bad conditions
MAX_BAD_HOURS = 12  # i.e. 3 real minutes (12 * 15 seconds / 60 = 3 simulated minutes; adjust as needed)

# Default environment settings
plant_types = {
    "cactus": {"ideal_ph": 6.0, "min_hum": 20, "max_hum": 40, "dry_rate": 0.5},
    "tropical": {"ideal_ph": 6.5, "min_hum": 60, "max_hum": 90, "dry_rate": 0.2},
    "herb": {"ideal_ph": 6.8, "min_hum": 50, "max_hum": 80, "dry_rate": 0.3},
    "default": {"ideal_ph": 6.5, "min_hum": 40, "max_hum": 70, "dry_rate": 0.3}
}

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

def initialize_plants(client):
    """Auto-initialize 30 plants on startup and publish their initial states as a burst."""
    plant_type_keys = list(plant_types.keys())
    for i in range(1, 31):  # 30 plants: plant01 to plant30
        plant_id = f"plant{i:02d}"
        if plant_id not in active_plants:
            plant_type = random.choice(plant_type_keys)
            env = plant_types[plant_type]

            active_plants[plant_id] = {
                "type": plant_type,
                "humidity": random.uniform(env["min_hum"], env["max_hum"]),
                "ph": random.uniform(6.0, 7.5),
                "temperature": random.uniform(18, 28),
                "bad_hours": 0,
                "alive": True
            }

            print(f"🌱 Initialized {plant_id} of type '{plant_type}'")

    # Burst publish initial states for all 30 plants
    print("🚀 Burst publishing initial states for 30 plants...")
    for plant_id, plant in active_plants.items():
        sensor_data = {
            "event": "sensor_update",
            "plantId": plant_id,
            "type": plant["type"],
            "humidity": round(plant["humidity"], 1),
            "ph": round(plant["ph"], 2),
            "temperature": round(plant["temperature"], 1),
            "alive": plant["alive"],
            "bad_hours": plant["bad_hours"],
            "timestamp": time.time()
        }
        client.publish(TOPIC, json.dumps(sensor_data), qos=1)
        print(f"📡 Initial publish for {plant_id}: {sensor_data}")

    # Publish startup event with all plants
    client.publish(TOPIC, json.dumps({
        "event": "simulator_started",
        "plants": list(active_plants.keys()),
        "num_plants": 30,
        "timestamp": time.time()
    }), qos=1)
    print("✅ Initial burst complete – 30 messages published!")

def update_plants(client):
    """Simulate changes in plant conditions and publish updates as a burst."""
    while True:
        for plant_id, plant in list(active_plants.items()):
            env = plant_types.get(plant["type"], plant_types["default"])

            # Skip dead plants (but still publish their final state if needed)
            if not plant["alive"]:
                continue

            # Simulate temperature impact on drying (scaled for 15s interval)
            temp_effect = (plant["temperature"] - 20) * 0.02
            humidity_loss = (env["dry_rate"] + temp_effect) * random.uniform(0.8, 1.2) * (update_interval / 10.0)  # Scale from original 10s
            plant["humidity"] -= humidity_loss
            plant["humidity"] = max(0, plant["humidity"])

            # Random pH drift (scaled)
            plant["ph"] += random.uniform(-0.05, 0.05) * (update_interval / 10.0)
            plant["ph"] = max(4.0, min(8.0, plant["ph"]))

            # Check for bad conditions
            bad = (
                plant["humidity"] < env["min_hum"]
                or plant["humidity"] > env["max_hum"]
                or abs(plant["ph"] - env["ideal_ph"]) > 1.0
            )

            if bad:
                plant["bad_hours"] += (update_interval / 10.0)  # Scale bad hours
                print(f"⚠️ {plant_id} is in bad condition ({plant['bad_hours']:.1f}h).")
                if plant["bad_hours"] >= MAX_BAD_HOURS:
                    plant["alive"] = False
                    print(f"💀 {plant_id} has died after prolonged bad conditions.")
                    # Publish death event
                    client.publish(TOPIC, json.dumps({
                        "event": "plant_dead",
                        "plantId": plant_id,
                        "timestamp": time.time()
                    }), qos=1)
            else:
                plant["bad_hours"] = 0  # reset counter if back to normal

            # Publish current sensor state
            sensor_data = {
                "event": "sensor_update",
                "plantId": plant_id,
                "type": plant["type"],
                "humidity": round(plant["humidity"], 1),
                "ph": round(plant["ph"], 2),
                "temperature": round(plant["temperature"], 1),
                "alive": plant["alive"],
                "bad_hours": round(plant["bad_hours"], 1),
                "timestamp": time.time()
            }
            client.publish(TOPIC, json.dumps(sensor_data), qos=1)
            print(f"📡 Burst update for {plant_id}: {sensor_data}")

        print(f"📊 Burst cycle complete – published updates for {len(active_plants)} plants")
        time.sleep(update_interval)  # Bursts every 15 seconds

# ---------------- MQTT HANDLERS ---------------- #

def on_connect(client, userdata, flags, reason_code, properties=None):
    print(f"✅ Connected to MQTT broker ({BROKER}) with code {reason_code}")
    if reason_code == 0:
        client.subscribe(TOPIC)
        # Initialize 30 plants and burst publish initials
        initialize_plants(client)
        print("🚀 Simulator ready – bursting 30 sensor updates every 15s")
    else:
        print(f"❌ Connection failed: {reason_code}")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        action = payload.get("action")
        plant_id = payload.get("plantId")

        if not plant_id:
            return

        if action == "add_plant":
            if plant_id not in active_plants:
                plant_type = payload.get("type", "default")
                environment = plant_types.get(plant_type, plant_types["default"])

                active_plants[plant_id] = {
                    "type": plant_type,
                    "humidity": random.uniform(environment["min_hum"], environment["max_hum"]),
                    "ph": random.uniform(6.0, 7.5),
                    "temperature": random.uniform(18, 28),
                    "bad_hours": 0,
                    "alive": True
                }

                print(f"🌱 Added new plant '{plant_id}' of type '{plant_type}'")
                # Publish confirmation
                client.publish(TOPIC, json.dumps({
                    "event": "plant_added",
                    "plantId": plant_id,
                    "type": plant_type,
                    "timestamp": time.time()
                }), qos=1)

        elif action == "water":
            amount = float(payload.get("amount", 0.3))
            water_plant(plant_id, amount)
            # Publish update
            if plant_id in active_plants:
                client.publish(TOPIC, json.dumps({
                    "event": "plant_watered",
                    "plantId": plant_id,
                    "humidity": active_plants[plant_id]["humidity"],
                    "timestamp": time.time()
                }), qos=1)

        elif action == "set_ph":
            target = float(payload.get("target", 6.5))
            regulate_ph(plant_id, target)
            # Publish update
            if plant_id in active_plants:
                client.publish(TOPIC, json.dumps({
                    "event": "ph_adjusted",
                    "plantId": plant_id,
                    "ph": active_plants[plant_id]["ph"],
                    "timestamp": time.time()
                }), qos=1)

        elif action == "set_temp":
            temp = float(payload.get("temp", 22))
            if plant_id in active_plants:
                active_plants[plant_id]["temperature"] = temp
                print(f"🌡️ Temperature for {plant_id} set to {temp}°C")
                # Publish update
                client.publish(TOPIC, json.dumps({
                    "event": "temp_adjusted",
                    "plantId": plant_id,
                    "temperature": temp,
                    "timestamp": time.time()
                }), qos=1)

    except Exception as e:
        print(f"⚠️ Error in on_message: {e}")

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