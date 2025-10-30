import paho.mqtt.client as mqtt
import ssl
import random
import json
import time
import uuid
from datetime import datetime, timedelta

# -------------------------------
# MQTT CONFIGURATION
# -------------------------------
BROKER = "broker.emqx.io"
PORT = 8883
CLIENT_ID = f"greenpulse-sim-{uuid.uuid4()}"
KEEPALIVE = 60

# -------------------------------
# ENVIRONMENT AND SEASONAL SETUP
# -------------------------------
ENVIRONMENTS = {
    "house": {"light": 0.7, "temp": 22, "humidity": 45},
    "garden": {"light": 1.0, "temp": 20, "humidity": 60},
    "greenhouse": {"light": 0.9, "temp": 26, "humidity": 70},
}

SEASON_TEMPS = {"spring": 7.2, "summer": 16.6, "autumn": 9.8, "winter": 1.7}
SEASON_AIR_HUMS = {"spring": 77, "summer": 76, "autumn": 83, "winter": 86}
SEASON_MAX_LIGHTS = {"spring": 85000, "summer": 100000, "autumn": 60000, "winter": 30000}

# -------------------------------
# PLANT STATE
# -------------------------------
active_plants = [f"plant{i:02d}" for i in range(1, 31)]
plant_water_state = {}
plant_ph_targets = {}
plant_humidity = {p: random.uniform(50, 80) for p in active_plants}
plant_ph = {p: random.uniform(6.0, 7.5) for p in active_plants}

# -------------------------------
# MQTT CALLBACKS
# -------------------------------
def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print("✅ Connected to MQTT Broker")
        client.subscribe("greenpulse/control/#")
    else:
        print(f"❌ Failed to connect: {rc}")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        action = payload.get("action")
        plant_id = payload.get("plantId")

        if not plant_id:
            return

        if action == "add_plant":
            if plant_id not in active_plants:
                active_plants.append(plant_id)
                plant_humidity[plant_id] = random.uniform(50, 80)
                plant_ph[plant_id] = random.uniform(6.0, 7.5)
                print(f"🌱 Added new plant: {plant_id}")

        elif action == "water":
            amount = float(payload.get("amount", 0.3))
            water_plant(plant_id, amount)

        elif action == "set_ph":
            target = float(payload.get("target", 6.5))
            regulate_ph(plant_id, target)

    except Exception as e:
        print(f"⚠️ Error in on_message: {e}")

# -------------------------------
# PLANT ACTIONS
# -------------------------------
def water_plant(plant_key, amount=0.3):
    """Increase soil humidity for a given plant."""
    plant_water_state[plant_key] = {
        "added_hum": amount,
        "timestamp": time.time()
    }
    print(f"💧 Watered {plant_key} (+{amount:.2f})")

def regulate_ph(plant_key, target_ph):
    """Set target pH value for a plant."""
    plant_ph_targets[plant_key] = target_ph
    print(f"⚗️ Regulating pH for {plant_key} → {target_ph:.2f}")

# -------------------------------
# DATA GENERATION
# -------------------------------
def generate_temperature(season, hour):
    base = SEASON_TEMPS[season]
    daily_variation = 4 * (1 - abs(12 - hour) / 12)
    noise = random.uniform(-1, 1)
    return base + daily_variation + noise

def generate_light(season, hour):
    daylight = SEASON_MAX_LIGHTS[season]
    if 6 <= hour <= 18:
        daylight_factor = max(0, 1 - abs(12 - hour) / 6)
        return daylight * daylight_factor
    return 0

def generate_air_humidity(season, hour):
    base = SEASON_AIR_HUMS[season]
    variation = 10 * (1 - abs(12 - hour) / 12)
    return base + random.uniform(-5, 5) + variation

def update_soil_humidity(current_hum, temp, light, plant_key):
    drying_factor = (temp / 30.0) + (light / 100000.0)
    new_hum = current_hum - random.uniform(0, drying_factor * 5)

    # Apply watering boost if exists
    if plant_key in plant_water_state:
        added = plant_water_state[plant_key]["added_hum"]
        elapsed = time.time() - plant_water_state[plant_key]["timestamp"]
        decay = max(0, 1 - elapsed / 300)  # ~5 min decay
        new_hum += added * 50 * decay  # convert to % moisture
        if decay <= 0:
            del plant_water_state[plant_key]

    return max(0, min(100, new_hum))

def update_ph(current_ph, plant_key):
    if plant_key in plant_ph_targets:
        target = plant_ph_targets[plant_key]
        current_ph += (target - current_ph) * 0.2
    return round(current_ph, 2)

# -------------------------------
# MQTT CLIENT SETUP
# -------------------------------
client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, CLIENT_ID)
client.on_connect = on_connect
client.on_message = on_message
client.tls_set(cert_reqs=ssl.CERT_NONE)
client.tls_insecure_set(True)
client.connect(BROKER, PORT, KEEPALIVE)
client.loop_start()

# -------------------------------
# MAIN SIMULATION LOOP
# -------------------------------
season = "summer"  # change as needed
simulated_time = datetime(2025, 1, 1, 6, 0, 0)  # start at 6 AM

print("🌿 Simulation running (1h = 10s)...\n")

try:
    while True:
        hour = simulated_time.hour
        total_temp = 0
        total_soil_hum = 0

        for plant_key in active_plants:
            env = random.choice(list(ENVIRONMENTS.keys()))
            env_data = ENVIRONMENTS[env]

            temp = generate_temperature(season, hour)
            light = generate_light(season, hour)
            air_hum = generate_air_humidity(season, hour)

            # Update states
            plant_humidity[plant_key] = update_soil_humidity(plant_humidity[plant_key], temp, light, plant_key)
            plant_ph[plant_key] = update_ph(plant_ph[plant_key], plant_key)

            total_temp += temp
            total_soil_hum += plant_humidity[plant_key]

            # Publish MQTT messages
            base_topic = f"greenpulse/{env}/{plant_key}"
            client.publish(f"{base_topic}/temperature", json.dumps({"value": round(temp, 2)}))
            client.publish(f"{base_topic}/light", json.dumps({"value": round(light, 2)}))
            client.publish(f"{base_topic}/air_humidity", json.dumps({"value": round(air_hum, 2)}))
            client.publish(f"{base_topic}/soil_humidity", json.dumps({"value": round(plant_humidity[plant_key], 2)}))
            client.publish(f"{base_topic}/ph", json.dumps({"value": round(plant_ph[plant_key], 2)}))

        avg_temp = total_temp / len(active_plants)
        avg_soil = total_soil_hum / len(active_plants)
        print(f"🕒 Sim Time: {simulated_time.strftime('%H:%M')} | 🌡️ {avg_temp:.1f}°C | 💧 {avg_soil:.1f}% | 🌱 {len(active_plants)} plants")

        # Advance simulated time by 1 hour every 10 seconds
        simulated_time += timedelta(hours=1)
        time.sleep(10)

except KeyboardInterrupt:
    print("🛑 Simulation stopped.")
    client.loop_stop()
    client.disconnect()
