import json
import random
import time
import threading
import ssl
import uuid
import paho.mqtt.client as mqtt

# ---------------- MQTT CONFIG ---------------- #
BROKER = "broker.emqx.io"
PORT = 8883
CLIENT_ID = f"greenpulse-sim-{uuid.uuid4()}"
KEEPALIVE = 60
TOPIC = "greenpulse/simulator"

# ---------------- PLANT DATA ---------------- #
active_plants = {}
update_interval = 10  # 10 seconds = 1 simulated hour

# How long (in simulated hours) a plant can survive bad conditions
MAX_BAD_HOURS = 12  # i.e. 2 real minutes (12 * 10 seconds)

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


def update_plants(client):
    """Simulate hourly changes in plant conditions."""
    while True:
        to_remove = []
        for plant_id, plant in list(active_plants.items()):
            env = plant_types.get(plant["type"], plant_types["default"])

            # Skip dead plants
            if not plant["alive"]:
                continue

            # Simulate temperature impact on drying
            temp_effect = (plant["temperature"] - 20) * 0.02
            humidity_loss = (env["dry_rate"] + temp_effect) * random.uniform(0.8, 1.2)
            plant["humidity"] -= humidity_loss
            plant["humidity"] = max(0, plant["humidity"])

            # Random pH drift
            plant["ph"] += random.uniform(-0.05, 0.05)
            plant["ph"] = max(4.0, min(8.0, plant["ph"]))

            # Check for bad conditions
            bad = (
                plant["humidity"] < env["min_hum"]
                or plant["humidity"] > env["max_hum"]
                or abs(plant["ph"] - env["ideal_ph"]) > 1.0
            )

            if bad:
                plant["bad_hours"] += 1
                print(f"⚠️ {plant_id} is in bad condition ({plant['bad_hours']}h).")
                if plant["bad_hours"] >= MAX_BAD_HOURS:
                    plant["alive"] = False
                    print(f"💀 {plant_id} has died after prolonged bad conditions.")
                    client.publish(TOPIC, json.dumps({
                        "event": "plant_dead",
                        "plantId": plant_id
                    }))
            else:
                plant["bad_hours"] = 0  # reset counter if back to normal

            # Print status
            status = "DEAD" if not plant["alive"] else "OK"
            print(f"🌿 {plant_id} | Type: {plant['type']} | Humidity: {plant['humidity']:.1f}% | pH: {plant['ph']:.2f} | Temp: {plant['temperature']}°C | {status}")

        time.sleep(update_interval)  # 1 hour passes every 10 seconds


# ---------------- MQTT HANDLERS ---------------- #

def on_connect(client, userdata, flags, reason_code, properties=None):
    print(f"✅ Connected to MQTT broker ({BROKER}) with code {reason_code}")
    client.subscribe(TOPIC)


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

        elif action == "water":
            amount = float(payload.get("amount", 0.3))
            water_plant(plant_id, amount)

        elif action == "set_ph":
            target = float(payload.get("target", 6.5))
            regulate_ph(plant_id, target)

        elif action == "set_temp":
            temp = float(payload.get("temp", 22))
            if plant_id in active_plants:
                active_plants[plant_id]["temperature"] = temp
                print(f"🌡️ Temperature for {plant_id} set to {temp}°C")

    except Exception as e:
        print(f"⚠️ Error in on_message: {e}")


# ---------------- MAIN ---------------- #

def main():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, CLIENT_ID)
    client.on_connect = on_connect
    client.on_message = on_message

    client.tls_set(cert_reqs=ssl.CERT_NONE)
    client.tls_insecure_set(True)
    client.connect(BROKER, PORT, KEEPALIVE)
    client.loop_start()

    # Start background simulation
    threading.Thread(target=update_plants, args=(client,), daemon=True).start()

    # Keep running
    while True:
        time.sleep(1)


if __name__ == "__main__":
    main()
