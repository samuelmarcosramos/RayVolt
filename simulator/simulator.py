import json
import os
import time

import paho.mqtt.client as mqtt

BROKER_HOST = os.getenv("MQTT_HOST", "localhost")
BROKER_PORT = int(os.getenv("MQTT_PORT", "1883"))
TOPIC = "rayvolt/telemetry/test"


def connect():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    # The broker may still be starting, so keep trying
    while True:
        try:
            client.connect(BROKER_HOST, BROKER_PORT)
            return client
        except OSError:
            print(f"Broker {BROKER_HOST}:{BROKER_PORT} not ready, retrying...")
            time.sleep(2)


def main():
    client = connect()
    client.loop_start()
    print(f"Connected to {BROKER_HOST}:{BROKER_PORT}, publishing to {TOPIC}")
    while True:
        message = json.dumps({"scooter": "test", "battery": 100})
        client.publish(TOPIC, message, qos=1)
        print(f"Sent {message}")
        time.sleep(5)


if __name__ == "__main__":
    main()
