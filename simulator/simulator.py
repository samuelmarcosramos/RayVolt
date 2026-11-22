import json
import math
import os
import random
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

BROKER_HOST = os.getenv("MQTT_HOST", "localhost")
BROKER_PORT = int(os.getenv("MQTT_PORT", "1883"))
PUBLISH_INTERVAL = float(os.getenv("PUBLISH_INTERVAL", "5"))
HUBS_TOPIC = "rayvolt/hubs"
FLEET_TOPIC = "rayvolt/fleet/+"
COMMANDS_TOPIC = "rayvolt/scooters/+/commands"
MIN_SPEED_KMH = 10
MAX_SPEED_KMH = 20
BATTERY_PER_100_M = 1


def distance_m(lat1, lon1, lat2, lon2):
    dx = (lon2 - lon1) * 111_320 * math.cos(math.radians(lat1))
    dy = (lat2 - lat1) * 110_540
    return math.hypot(dx, dy)


class Scooter:
    def __init__(self, scooter_id, hub):
        self.id = scooter_id
        self.battery = 100
        self.locked = True
        self.speed_kmh = 0
        self.hub_id = hub["id"]
        self.lat = hub["lat"]
        self.lon = hub["lon"]
        self.status = "AT_HUB"
        self.destination = None

    def unlock(self, hubs, rng=random):
        if not self.locked:
            return
        options = [hub for hub in hubs if hub["id"] != self.hub_id]
        if not options:
            print(f"Error: scooter {self.id} has no other hub to go to")
            return
        self.destination = rng.choice(options)
        self.locked = False
        self.status = "MOVING"

    def lock(self):
        self.locked = True
        self.speed_kmh = 0
        self.destination = None
        self.status = "AT_HUB" if self.hub_id is not None else "OUTSIDE_HUB"

    def step(self, seconds, rng=random):
        if self.destination is None:
            return
        self.speed_kmh = round(rng.uniform(MIN_SPEED_KMH, MAX_SPEED_KMH), 1)
        travelled = self.speed_kmh / 3.6 * seconds
        remaining = distance_m(self.lat, self.lon, self.destination["lat"], self.destination["lon"])
        if travelled >= remaining:
            travelled = remaining
            self.lat = self.destination["lat"]
            self.lon = self.destination["lon"]
            self.hub_id = self.destination["id"]
            self.status = "AT_HUB"
            self.speed_kmh = 0
            self.destination = None
        else:
            fraction = travelled / remaining
            self.hub_id = None
            self.lat += (self.destination["lat"] - self.lat) * fraction
            self.lon += (self.destination["lon"] - self.lon) * fraction
        self.battery = max(0, self.battery - travelled / 100 * BATTERY_PER_100_M)

    def telemetry_topic(self):
        return f"rayvolt/scooters/{self.id}/telemetry"

    def telemetry(self, now):
        return {
            "scooterId": self.id,
            "timestamp": now.isoformat(),
            "battery": round(self.battery),
            "locked": self.locked,
            "speedKmh": self.speed_kmh,
            "hubId": self.hub_id,
            "lat": round(self.lat, 6),
            "lon": round(self.lon, 6),
            "status": self.status,
        }


def scooter_id_from_topic(topic):
    parts = topic.split("/")
    return int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else None


class Fleet:
    def __init__(self):
        self.hubs = {}
        self._scooters = {}
        self._lock = threading.Lock()

    def handle_hubs_message(self, payload):
        try:
            self.hubs = {hub["id"]: hub for hub in json.loads(payload)}
        except (ValueError, KeyError, TypeError):
            print(f"Error: invalid hubs message {payload!r}")

    def handle_fleet_message(self, topic, payload):
        scooter_id = scooter_id_from_topic(topic)
        if scooter_id is None:
            print(f"Ignored fleet message on {topic}")
            return
        try:
            message = json.loads(payload)
            hub_id = message["hubId"]
        except (ValueError, KeyError, TypeError):
            print(f"Error: invalid message for scooter {scooter_id}: {payload!r}")
            return
        if message.get("status") == "RETIRED":
            with self._lock:
                self._scooters.pop(scooter_id, None)
            return
        hub = self.hubs.get(hub_id)
        if hub is None:
            print(f"Error: scooter {scooter_id} has unknown hub {hub_id}")
            return
        with self._lock:
            self._scooters.setdefault(scooter_id, Scooter(scooter_id, hub))

    def handle_command(self, topic, payload):
        scooter_id = scooter_id_from_topic(topic)
        try:
            command = json.loads(payload)["command"]
        except (ValueError, KeyError, TypeError):
            print(f"Error: invalid command on {topic}: {payload!r}")
            return
        with self._lock:
            scooter = self._scooters.get(scooter_id)
            if scooter is None:
                print(f"Error: command for unknown scooter {scooter_id}")
            elif command == "UNLOCK":
                scooter.unlock(self.hubs.values())
            elif command == "LOCK":
                scooter.lock()
            else:
                print(f"Error: unknown command {command} for scooter {scooter_id}")

    def tick(self, seconds, now):
        with self._lock:
            for scooter in self._scooters.values():
                scooter.step(seconds)
            return [(s.telemetry_topic(), s.telemetry(now)) for s in self._scooters.values()]

    def scooters(self):
        with self._lock:
            return list(self._scooters.values())


def on_message(client, fleet, msg):
    if msg.topic == HUBS_TOPIC:
        fleet.handle_hubs_message(msg.payload)
        client.subscribe([(FLEET_TOPIC, 1), (COMMANDS_TOPIC, 1)])
    elif msg.topic.startswith("rayvolt/fleet/"):
        fleet.handle_fleet_message(msg.topic, msg.payload)
    else:
        fleet.handle_command(msg.topic, msg.payload)


def connect(fleet):
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.on_connect = lambda c, *_: c.subscribe(HUBS_TOPIC, qos=1)
    client.on_message = lambda c, u, msg: on_message(c, fleet, msg)
    while True:
        try:
            client.connect(BROKER_HOST, BROKER_PORT)
            return client
        except OSError:
            print(f"Broker {BROKER_HOST}:{BROKER_PORT} not ready, retrying...")
            time.sleep(2)


def main():
    fleet = Fleet()
    client = connect(fleet)
    client.loop_start()
    print(f"Connected to {BROKER_HOST}:{BROKER_PORT}, waiting for hubs on {HUBS_TOPIC}")
    while True:
        now = datetime.now(timezone.utc)
        for topic, telemetry in fleet.tick(PUBLISH_INTERVAL, now):
            message = json.dumps(telemetry)
            client.publish(topic, message, qos=1)
            print(f"Sent {message}")
        time.sleep(PUBLISH_INTERVAL)


if __name__ == "__main__":
    main()
