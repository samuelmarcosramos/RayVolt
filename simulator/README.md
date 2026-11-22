# Telemetry simulator

Python service that plays the role of the scooters. It talks to the backend only through the MQTT broker.

## Topics

| Topic | From | To | Retained | Message |
|---|---|---|---|---|
| `rayvolt/hubs` | backend | simulator | yes | `[{"id": 1, "name": "Main Gate", "lat": 40.612, "lon": -8.7175}, ...]` |
| `rayvolt/fleet/{id}` | backend | simulator | yes | `{"hubId": 1}` or `{"hubId": 1, "status": "RETIRED"}` |
| `rayvolt/scooters/{id}/commands` | backend | simulator | no | `{"command": "UNLOCK"}`, `"LOCK"` or `"FAULT"` |
| `rayvolt/scooters/{id}/telemetry` | simulator | backend | no | every 5 s, see below |
| `rayvolt/scooters/{id}/events` | simulator | backend | no | `{"scooterId": 7, "timestamp": "...", "type": "FAULT"}` |

Telemetry:

```json
{"scooterId": 7, "timestamp": "2026-11-22T10:00:00+00:00", "battery": 100, "locked": true,
 "speedKmh": 0, "hubId": 1, "lat": 40.612, "lon": -8.7175, "status": "AT_HUB"}
```

`status` is `AT_HUB`, `MOVING` or `OUTSIDE_HUB`. `hubId` is `null` when the scooter is not at a hub.

## Behaviour

- The simulator reads the hubs first, then the fleet.
- `UNLOCK`: the scooter rides in a straight line to a random other hub, at 10-20 km/h, using 1% battery per 100 m.
- `LOCK`: the scooter locks where it is.
- `FAULT`: only sends the fault event, the scooter keeps going.

## Run and test

```
docker compose up -d broker simulator          # from the repo root
pip install -r requirements-dev.txt && pytest  # from simulator/
```

Environment: `MQTT_HOST` (default `localhost`), `MQTT_PORT` (`1883`), `PUBLISH_INTERVAL` (`5`).
