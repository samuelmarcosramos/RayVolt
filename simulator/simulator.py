import json
import os
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

BROKER_HOST = os.getenv("MQTT_HOST", "localhost")
BROKER_PORT = int(os.getenv("MQTT_PORT", "1883"))
PUBLISH_INTERVAL = float(os.getenv("PUBLISH_INTERVAL", "5"))
HUBS_TOPIC = "rayvolt/hubs"
FLEET_TOPIC = "rayvolt/fleet/+"


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

    def telemetry_topic(self):
        return f"rayvolt/scooters/{self.id}/telemetry"

    def telemetry(self, now):
        return {
            "scooterId": self.id,
            "timestamp": now.isoformat(),
            "battery": self.battery,
            "locked": self.locked,
            "speedKmh": self.speed_kmh,
            "hubId": self.hub_id,
            "lat": self.lat,
            "lon": self.lon,
            "status": self.status,
        }


def scooter_id_from_topic(topic):
    last = topic.split("/")[-1]
    return int(last) if last.isdigit() else None


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

    def scooters(self):
        with self._lock:
            return list(self._scooters.values())


def on_message(client, fleet, msg):
    if msg.topic == HUBS_TOPIC:
        fleet.handle_hubs_message(msg.payload)
        client.subscribe(FLEET_TOPIC, qos=1)
    else:
        fleet.handle_fleet_message(msg.topic, msg.payload)


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
        for scooter in fleet.scooters():
            message = json.dumps(scooter.telemetry(now))
            client.publish(scooter.telemetry_topic(), message, qos=1)
            print(f"Sent {message}")
        time.sleep(PUBLISH_INTERVAL)


if __name__ == "__main__":
    main()
