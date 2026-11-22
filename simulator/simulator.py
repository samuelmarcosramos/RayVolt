import json
import os
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

BROKER_HOST = os.getenv("MQTT_HOST", "localhost")
BROKER_PORT = int(os.getenv("MQTT_PORT", "1883"))
PUBLISH_INTERVAL = float(os.getenv("PUBLISH_INTERVAL", "5"))
FLEET_TOPIC = "rayvolt/fleet/+"


class Scooter:
    def __init__(self, scooter_id):
        self.id = scooter_id
        self.battery = 100
        self.locked = True
        self.speed_kmh = 0

    def telemetry_topic(self):
        return f"rayvolt/scooters/{self.id}/telemetry"

    def telemetry(self, now):
        return {
            "scooterId": self.id,
            "timestamp": now.isoformat(),
            "battery": self.battery,
            "locked": self.locked,
            "speedKmh": self.speed_kmh,
        }


def scooter_id_from_topic(topic):
    last = topic.split("/")[-1]
    return int(last) if last.isdigit() else None


class Fleet:
    def __init__(self):
        self._scooters = {}
        self._lock = threading.Lock()

    def handle_fleet_message(self, topic, payload):
        scooter_id = scooter_id_from_topic(topic)
        if scooter_id is None:
            print(f"Ignored fleet message on {topic}")
            return
        with self._lock:
            if payload:
                self._scooters.setdefault(scooter_id, Scooter(scooter_id))
            else:
                self._scooters.pop(scooter_id, None)

    def scooters(self):
        with self._lock:
            return list(self._scooters.values())


def connect(fleet):
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.on_connect = lambda c, *_: c.subscribe(FLEET_TOPIC, qos=1)
    client.on_message = lambda c, u, msg: fleet.handle_fleet_message(msg.topic, msg.payload)
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
    print(f"Connected to {BROKER_HOST}:{BROKER_PORT}, waiting for scooters on {FLEET_TOPIC}")
    while True:
        now = datetime.now(timezone.utc)
        for scooter in fleet.scooters():
            message = json.dumps(scooter.telemetry(now))
            client.publish(scooter.telemetry_topic(), message, qos=1)
            print(f"Sent {message}")
        time.sleep(PUBLISH_INTERVAL)


if __name__ == "__main__":
    main()
