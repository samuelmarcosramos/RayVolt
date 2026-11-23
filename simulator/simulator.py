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
CHARGE_PER_MINUTE = float(os.getenv("CHARGE_PER_MINUTE", "3"))
SEED = os.getenv("SEED")
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
        self.fault = False

    def unlock(self, hubs, rng=random):
        if not self.locked:
            return None
        if self.fault:
            return "SCOOTER_FAULT"
        if round(self.battery) == 0:
            return "NO_BATTERY"
        options = [hub for hub in hubs if hub["id"] != self.hub_id]
        if not options:
            return "NO_OTHER_HUB"
        self.destination = rng.choice(options)
        self.locked = False
        self.status = "MOVING"
        return None

    def lock(self):
        self.locked = True
        self.speed_kmh = 0
        self.destination = None
        self.status = "AT_HUB" if self.hub_id is not None else "OUTSIDE_HUB"

    def step(self, seconds, rng=random):
        if self.destination is None:
            if self.locked and self.status == "AT_HUB":
                self.battery = min(100, self.battery + CHARGE_PER_MINUTE * seconds / 60)
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

    def events_topic(self):
        return f"rayvolt/scooters/{self.id}/events"

    def fault_event(self, now):
        return {"scooterId": self.id, "timestamp": now.isoformat(), "type": "FAULT"}

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
            "fault": self.fault,
        }


def scooter_id_from_topic(topic):
    parts = topic.split("/")
    return int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else None


class Fleet:
    def __init__(self, rng=None):
        self.rng = rng or random.Random()
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

    def handle_command(self, topic, payload, now):
        scooter_id = scooter_id_from_topic(topic)
        try:
            message = json.loads(payload)
            command_id = message["commandId"]
        except (ValueError, KeyError, TypeError):
            return []
        command = message.get("command")
        messages = []
        with self._lock:
            scooter = self._scooters.get(scooter_id)
            if scooter is None:
                reason = "UNKNOWN_SCOOTER"
            elif command == "UNLOCK":
                reason = scooter.unlock(self.hubs.values(), self.rng)
            elif command == "LOCK":
                scooter.lock()
                reason = None
            elif command == "FAULT":
                if not scooter.fault:
                    scooter.fault = True
                    messages.append((scooter.events_topic(), scooter.fault_event(now)))
                reason = None
            elif command == "CLEAR_FAULT":
                scooter.fault = False
                reason = None
            else:
                reason = "UNKNOWN_COMMAND"
        response = {
            "scooterId": scooter_id,
            "timestamp": now.isoformat(),
            "commandId": command_id,
            "command": command,
            "status": "REJECTED" if reason else "COMPLETED",
            "reason": reason,
        }
        messages.append((f"rayvolt/scooters/{scooter_id}/responses", response))
        return messages

    def tick(self, seconds, now):
        with self._lock:
            for scooter in self._scooters.values():
                scooter.step(seconds, self.rng)
            return [(s.telemetry_topic(), s.telemetry(now)) for s in self._scooters.values()]

    def scooters(self):
        with self._lock:
            return list(self._scooters.values())


class RateMeter:
    def __init__(self, period=10, clock=time.monotonic):
        self.period = period
        self.clock = clock
        self.start = clock()
        self.sent = 0
        self.acked = 0
        self._lock = threading.Lock()

    def add_sent(self, readings):
        with self._lock:
            self.sent += readings

    def add_acked(self):
        with self._lock:
            self.acked += 1

    def report(self, target):
        with self._lock:
            elapsed = self.clock() - self.start
            if elapsed < self.period:
                return None
            rate = self.acked / elapsed
            line = (f"Rate: {rate:.0f} readings/s (target {target:.0f}): "
                    f"{self.sent} sent, {self.acked} acknowledged in {elapsed:.0f} s")
            self.start = self.clock()
            self.sent = 0
            self.acked = 0
        if rate < 0.95 * target:
            return f"Warning: {line}"
        return line


def on_message(client, fleet, msg):
    if msg.topic == HUBS_TOPIC:
        fleet.handle_hubs_message(msg.payload)
        client.subscribe([(FLEET_TOPIC, 1), (COMMANDS_TOPIC, 1)])
    elif msg.topic.startswith("rayvolt/fleet/"):
        fleet.handle_fleet_message(msg.topic, msg.payload)
    else:
        for topic, message in fleet.handle_command(msg.topic, msg.payload, datetime.now(timezone.utc)):
            client.publish(topic, json.dumps(message), qos=1)


def connect(fleet, meter):
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.on_publish = lambda *_: meter.add_acked()
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
    fleet = Fleet(random.Random(SEED) if SEED else None)
    meter = RateMeter()
    client = connect(fleet, meter)
    client.loop_start()
    print(f"Connected to {BROKER_HOST}:{BROKER_PORT}, waiting for hubs on {HUBS_TOPIC}")
    next_tick = time.monotonic()
    while True:
        rate = meter.report(len(fleet.scooters()) / PUBLISH_INTERVAL)
        if rate:
            print(rate)
        now = datetime.now(timezone.utc)
        readings = fleet.tick(PUBLISH_INTERVAL, now)
        for topic, telemetry in readings:
            message = json.dumps(telemetry)
            client.publish(topic, message, qos=1)
            print(f"Sent {message}")
        meter.add_sent(len(readings))
        next_tick += PUBLISH_INTERVAL
        time.sleep(max(0, next_tick - time.monotonic()))


if __name__ == "__main__":
    main()
