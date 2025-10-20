import time
import json
import random
import threading
import paho.mqtt.client as mqtt
from datetime import datetime, timedelta
import math  # For sinusoidal day/night

BROKER = "localhost"
PORT = 1883
TOPIC_BASE = "plants"
CONTROL_TOPIC = "control/add_plant"
PUBLISH_INTERVAL = 5  # seconds between publishes
TIME_SPEED_FACTOR = 10
DELTA_SIM_SECONDS = PUBLISH_INTERVAL * TIME_SPEED_FACTOR
DELTA_SIM_HOURS = DELTA_SIM_SECONDS / 3600.0
PUBLISHES_PER_SIM_DAY = int(86400 / DELTA_SIM_SECONDS)

# Plant types with dry multipliers (faster dry = higher multiplier)
PLANT_TYPES = {
    "tomato": 1.2,
    "basil": 1.0,
    "rose": 0.9,
    "succulent": 0.7,
    "fern": 1.1,
    "orchid": 0.8
}

class PlantPublisher(threading.Thread):
    def __init__(self, plant_id, environment, plant_type, initial_moisture=70, initial_ph=6.5):
        super().__init__()
        self.plant_id = plant_id
        self.environment = environment
        self.plant_type = plant_type
        self.client_id = f"{environment}-{plant_id}"
        self.client = mqtt.Client(self.client_id)
        self.client.connect(BROKER, PORT, 60)
        self.client.loop_start()
        self.running = True

        # Simulated state
        self.current_soil_moisture = initial_moisture
        self.current_ph = initial_ph
        self.base_dry_rate_per_hour = self._get_base_dry_rate()
        self.dry_multiplier = PLANT_TYPES.get(plant_type, 1.0)
        self.rain_per_publish = 0.0
        self.last_day = None
        self.is_rainy_day = False

        # Start simulated time
        self.simulated_time = datetime.now()

    def _get_base_dry_rate(self):
        rates = {"indoor": 0.3, "outdoor": 0.8, "greenhouse": 0.5}
        return rates.get(self.environment, 0.5)

    def _update_rain_status(self):
        current_day = self.simulated_time.date()
        if current_day != self.last_day:
            self.last_day = current_day
            self.is_rainy_day = (self.environment == "outdoor") and (random.random() < 0.23)
            if self.is_rainy_day:
                daily_boost = random.uniform(10, 20)
                self.rain_per_publish = daily_boost / PUBLISHES_PER_SIM_DAY
            else:
                self.rain_per_publish = 0.0

    def _apply_hysteresis(self, new_value, current_value, threshold=0.5):
        delta = abs(new_value - current_value)
        if delta < threshold:
            return current_value + (new_value - current_value) * 0.3
        return new_value

    def _is_daytime(self, t):
        hour = t.hour + (t.minute / 60)
        return 6 <= hour <= 18

    def _sinusoidal_variation(self, t, amplitude, period=24):
        hour = t.hour + (t.minute / 60)
        return amplitude * math.sin(2 * math.pi * hour / period)

    def generate_data(self):
        self._update_rain_status()
        t = self.simulated_time

        # Temperature
        if self.environment == "indoor":
            base_temp = 22
            temp_cycle = self._sinusoidal_variation(t, 2)
            temperature = base_temp + temp_cycle + random.uniform(-1, 1)
        elif self.environment == "outdoor":
            base_temp = 18
            temp_cycle = self._sinusoidal_variation(t, 10)
            temperature = base_temp + temp_cycle + random.uniform(-2, 2)
            if random.random() < 0.001:
                temperature += 5
        else:  # greenhouse
            base_temp = 22
            temp_cycle = self._sinusoidal_variation(t, 4)
            temperature = base_temp + temp_cycle + random.uniform(-0.5, 0.5)

        # Soil Moisture: Temp-dependent dry + type mult + rain + noise + hysteresis
        temp_multiplier = max(0.5, 1 + (temperature - 20) / 20)
        dry_amount = self.base_dry_rate_per_hour * self.dry_multiplier * temp_multiplier * DELTA_SIM_HOURS
        updated_moisture = max(0, self.current_soil_moisture - dry_amount + self.rain_per_publish)
        noisy_moisture = updated_moisture + random.uniform(-0.5, 0.5)
        self.current_soil_moisture = self._apply_hysteresis(noisy_moisture, self.current_soil_moisture, threshold=0.5)
        self.current_soil_moisture = max(0, min(100, self.current_soil_moisture))

        # pH: Slow drift + env bias + rain nudge + noise + hysteresis
        ph_drift = random.uniform(-0.05, 0.05) * DELTA_SIM_HOURS  # Base drift per hour
        if self.environment == "outdoor":
            ph_drift -= 0.01 * DELTA_SIM_HOURS  # Slight acidic bias
        elif self.environment == "indoor":
            ph_drift += 0.005 * DELTA_SIM_HOURS  # Neutral bias
        # Rain nudge: +0.05 total on rainy days
        rain_ph_nudge = 0.05 / PUBLISHES_PER_SIM_DAY if self.is_rainy_day else 0
        updated_ph = self.current_ph + ph_drift + rain_ph_nudge
        noisy_ph = updated_ph + random.uniform(-0.02, 0.02)
        self.current_ph = self._apply_hysteresis(noisy_ph, self.current_ph, threshold=0.05)
        self.current_ph = max(4.0, min(8.0, self.current_ph))

        data = {
            "plantId": self.plant_id,
            "environment": self.environment,
            "plantType": self.plant_type,
            "sensors": {
                "temperature": round(temperature, 2),
                "soilMoisture": round(self.current_soil_moisture, 2),
                "pH": round(self.current_ph, 2)
            },
            "timestamp": t.isoformat()
        }
        return data

    def run(self):
        while self.running:
            data = self.generate_data()
            payload = json.dumps(data)
            topic = f"{TOPIC_BASE}/{self.environment}/{self.plant_id}"

            self.client.publish(topic, payload)
            print(f"[{self.client_id}] 📡 Published to {topic}: {payload}")

            self.simulated_time += timedelta(seconds=DELTA_SIM_SECONDS)
            time.sleep(PUBLISH_INTERVAL)

    def stop(self):
        self.running = False
        self.client.loop_stop()
        self.client.disconnect()

class DynamicPublisherFactory:
    def __init__(self, num_plants_per_env=10):
        self.publishers = []
        self.num_plants_per_env = num_plants_per_env
        self.environments = ["indoor", "outdoor", "greenhouse"]
        self.next_plant_id = 1  # For dynamic unique IDs

        # Control client for dynamic adds
        self.control_client = mqtt.Client("factory-control")
        self.control_client.on_message = self._on_control_message
        self.control_client.connect(BROKER, PORT, 60)
        self.control_client.subscribe(CONTROL_TOPIC)
        self.control_client.loop_start()

        # Initial creation
        self._create_initial_publishers()

    def _create_initial_publishers(self):
        for env in self.environments:
            for _ in range(self.num_plants_per_env):
                self._add_plant(env, random.choice(list(PLANT_TYPES.keys())))

    def _add_plant(self, environment, plant_type, initial_moisture=None, initial_ph=None):
        plant_id = f"plant-{self.next_plant_id:02d}"
        self.next_plant_id += 1
        initial_moisture = initial_moisture or random.uniform(65, 75)
        initial_ph = initial_ph or 6.5
        pub = PlantPublisher(plant_id, environment, plant_type, initial_moisture, initial_ph)
        self.publishers.append(pub)
        pub.start()
        print(f"[control] Added new plant: {environment}-{plant_id} ({plant_type}) | Moisture: {initial_moisture:.1f}%, pH: {initial_ph:.1f}")
        return pub

    def _on_control_message(self, client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode())
            env = payload.get("environment", "indoor")
            plant_type = payload.get("plantType", random.choice(list(PLANT_TYPES.keys())))
            initial_moisture = payload.get("initialMoisture", None)
            initial_ph = payload.get("initialPh", None)
            if env in self.environments and plant_type in PLANT_TYPES:
                self._add_plant(env, plant_type, initial_moisture, initial_ph)
            else:
                print(f"[control] Invalid add request: {payload}")
        except json.JSONDecodeError:
            print(f"[control] Invalid JSON in {msg.topic}: {msg.payload.decode()}")

    def start_all(self):
        print(f"Started {len(self.publishers)} initial publishers (30 plants, 90 sensors). Listening for dynamic adds on {CONTROL_TOPIC}.")

    def stop_all(self):
        for p in self.publishers:
            p.stop()
        for p in self.publishers:
            p.join()
        self.control_client.loop_stop()
        self.control_client.disconnect()
        print("All publishers stopped.")

if __name__ == "__main__":
    factory = DynamicPublisherFactory(num_plants_per_env=10)
    factory.start_all()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Shutting down...")
        factory.stop_all()