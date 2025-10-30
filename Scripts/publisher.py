import time
import json
import random
import math
import paho.mqtt.client as mqtt
from datetime import datetime
from paho.mqtt.client import CallbackAPIVersion  # Correct import for VERSION2
import uuid  # For unique CLIENT_ID
import ssl  # For TLS

# Configuration
BROKER = "broker.emqx.io"  # EMQX public broker
PORT = 8883  # TLS port
CLIENT_ID = str(uuid.uuid4())  # Unique ID each run
ENVIRONMENTS = ["house", "garden", "greenhouse"]
SENSORS = ["light", "temperature", "ph", "humidity"]  # Added temperature, humidity now simulates soil moisture

# Topic for new plant notifications
NEW_PLANT_TOPIC = "greenpulse/control/newplant"

# Season selection for lifelike simulation based on Aarhus, Denmark historical data
print("Available seasons: winter, spring, summer, autumn")
SEASON = input("Enter season (default: autumn): ").lower().strip() or "autumn"
if SEASON not in ["winter", "spring", "summer", "autumn"]:
    SEASON = "autumn"
    print(f"Invalid season, defaulting to {SEASON}")

# Historical averages for Aarhus, Denmark (approximated from monthly data)
SEASON_TEMPS = {
    "winter": 1.7,   # Dec-Feb avg
    "spring": 7.2,   # Mar-May avg
    "summer": 16.6,  # Jun-Aug avg
    "autumn": 9.8    # Sep-Nov avg
}
SEASON_AIR_HUMS = {
    "winter": 86,    # High humidity in winter
    "spring": 77,
    "summer": 76,    # Lower in summer
    "autumn": 83
}
SEASON_MAX_LIGHTS = {  # Approximate peak daily lux for outdoor (full sun ~100k lux, adjusted)
    "winter": 15000,
    "spring": 50000,
    "summer": 75000,
    "autumn": 35000
}

# Optional: Callback for connection status
def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        print("Connected successfully")
        # Subscribe to new plant topic after connect
        client.subscribe(NEW_PLANT_TOPIC, qos=1)
        print(f"Subscribed to {NEW_PLANT_TOPIC} for new plant notifications")
    else:
        print(f"Connection failed with reason: {reason_code}")

# Callback for incoming messages (new plants)
def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload.decode())
        if data.get('action') == 'add_plant':
            plant_id = data.get('plantId')
            if plant_id and plant_id not in history:
                history[plant_id] = {sensor: [] for sensor in SENSORS}
                active_plants.append(plant_id)
                print(f"🌱 New plant added: {plant_id} (Environment: {data.get('environment', 'unknown')})")
            else:
                print(f"Plant {plant_id} already exists or invalid")
    except json.JSONDecodeError:
        print("Invalid JSON received on control topic")
    except Exception as e:
        print(f"Error processing message: {e}")

# Create MQTT client with v2 callback API
client = mqtt.Client(
    callback_api_version=CallbackAPIVersion.VERSION2,
    client_id=CLIENT_ID,
    protocol=mqtt.MQTTv5  # v5 for properties
)
client.tls_set(tls_version=ssl.PROTOCOL_TLSv1_2)  # Enable TLS (system CA certs)
client.on_connect = on_connect
client.on_message = on_message  # Enable message callback

# Connect
client.connect(BROKER, PORT, keepalive=60)
client.loop_start()

# Initial active plants (1-30)
active_plants = [f"plant{i:02d}" for i in range(1, 31)]
# Historical trends (simple dict for demo; use DB in production) - now includes temperature
history = {plant_id: {sensor: [] for sensor in SENSORS} for plant_id in active_plants}

print(f"Simulation started in {SEASON} season for Aarhus, Denmark weather patterns.")
print(f"Initial plants: {len(active_plants)}. Send JSON to {NEW_PLANT_TOPIC} like: {{'action':'add_plant', 'plantId':'plant31', 'environment':'garden'}}")

try:
    while True:
        for plant_key in active_plants[:]:  # Copy list to allow additions during loop if needed
            env = random.choice(ENVIRONMENTS)  # Random environment per reading (could persist per plant)
            
            # Daily cycle: Sine wave based on hour (0-23), peaks midday
            hour = datetime.now().hour
            cycle_factor = math.sin(2 * math.pi * (hour + 6) / 24)  # Shifted to peak around noon (hour 12)
            
            # Temperature (influenced by season for outdoor)
            if env == "house":
                temp = 20 + cycle_factor * 3 + random.uniform(-1, 1)  # Stable indoor
            else:
                base_temp = SEASON_TEMPS[SEASON]
                diurnal_variation = 6 * cycle_factor  # Larger day-night swing outdoor
                temp = base_temp + diurnal_variation + random.uniform(-1.5, 1.5)
            temp = round(temp, 1)
            
            # Light (lux, influenced by season and time of day for outdoor)
            if env == "house":
                # Indoor: moderate, varies slightly with time (artificial light)
                base_light = 400 + abs(cycle_factor) * 300
                light = base_light + random.uniform(-50, 50)
            else:
                # Outdoor: scales with season max and day cycle (night=0)
                max_daily = SEASON_MAX_LIGHTS[SEASON]
                daylight_fraction = max(0, (cycle_factor + 1) / 2)  # 0 at night, 1 at peak
                light = max_daily * daylight_fraction + random.uniform(-2000, 2000)
                if env == "greenhouse":
                    light *= 0.7  # Some shading
            light = max(0, round(light))
            
            # pH (stable, slight random variation)
            ph = 6.2 + random.uniform(-0.3, 0.3)
            ph = round(ph, 1)
            
            # Air humidity (for drying calculation, based on season)
            if env == "house":
                air_hum = 50 + cycle_factor * 5 + random.uniform(-2, 2)
            else:
                air_hum = SEASON_AIR_HUMS[SEASON] + cycle_factor * 3 + random.uniform(-2, 2)
            air_hum = round(air_hum, 1)
            
            # Soil humidity simulation (dries faster with higher temp, light, lower air hum)
            hum_hist = history[plant_key]["humidity"]
            if len(hum_hist) == 0:
                soil_hum = random.uniform(60, 85)  # Initial moisture
            else:
                soil_hum = hum_hist[-1]
            
            # Calculate moisture loss per interval (60s ~1 min; tuned for ~0.05-0.3% loss/min)
            temp_factor = max(0, (temp - 5) / 15)  # Higher temp increases evaporation
            light_factor = min(1, light / 50000)   # Light boosts transpiration
            air_factor = max(0.5, (100 - air_hum) / 30)  # Drier air increases drying
            loss = 0.02 * temp_factor * light_factor * air_factor  # % loss per minute
            
            soil_hum = max(10, soil_hum - loss)  # Clamp to min 10%
            humidity_value = round(soil_hum, 1)
            
            # Prediction based on soil humidity
            if humidity_value < 30:
                prediction = "water now"
            elif humidity_value < 45:
                prediction = "water soon"
            else:
                prediction = None
            
            # Store values
            values = {
                "light": light,
                "temperature": temp,
                "ph": ph,
                "humidity": humidity_value
            }
            for sensor in SENSORS:
                history[plant_key][sensor].append(values[sensor])
                if len(history[plant_key][sensor]) > 5:  # Keep last 5 for trend
                    history[plant_key][sensor] = history[plant_key][sensor][-5:]
                
                # Data payload
                data = {
                    "plantId": plant_key,
                    "environment": env,
                    "sensor": sensor,
                    "value": values[sensor],
                    "timestamp": time.time(),
                    "prediction": prediction if sensor == "humidity" else None,
                    "air_humidity": air_hum if sensor == "humidity" else None  # Extra for humidity readings
                }
                
                # Topic: Hierarchical for scalability
                topic = f"greenpulse/{env}/{plant_key}/{sensor}"
                
                # Publish
                payload = json.dumps(data)
                client.publish(topic, payload, qos=1)
                print(f"📡 Published to {topic}: {payload}")
        
        # Stats (demo: average soil humidity and temp across plants)
        if active_plants:
            avg_soil_hum = sum(
                (sum(h["humidity"]) / len(h["humidity"]) if len(h["humidity"]) > 0 else 0)
                for h in history.values()
            ) / len(active_plants)
            avg_temp = sum(
                (sum(h["temperature"]) / len(h["temperature"]) if len(h["temperature"]) > 0 else 0)
                for h in history.values()
            ) / len(active_plants)
            print(f"📊 Avg Soil Humidity: {round(avg_soil_hum, 1)}% | Avg Temp: {round(avg_temp, 1)}°C | Total Plants: {len(active_plants)}")
        else:
            print("📊 No active plants")

        time.sleep(60)  # Simulate interval (1 min; adjust for slower sim)

except KeyboardInterrupt:
    print("Publisher stopped.")
    client.loop_stop()
    client.disconnect()