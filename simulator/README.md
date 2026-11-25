# Telemetry simulator

Python service that plays the role of the scooters. It talks to the backend only through the MQTT broker.

## Topics

| Topic | From | To | Retained | Message |
|---|---|---|---|---|
| `rayvolt/hubs` | backend | simulator | yes | `[{"id": 1, "name": "Main Gate", "lat": 40.612, "lon": -8.7175}, ...]` |
| `rayvolt/fleet/{id}` | backend | simulator | yes | `{"hubId": 1}` or `{"hubId": 1, "status": "RETIRED"}` |
| `rayvolt/scooters/{id}/commands` | backend | simulator | no | `{"command": "UNLOCK", "commandId": "..."}`, see below |
| `rayvolt/scooters/{id}/responses` | simulator | backend | no | one reply per command, see below |
| `rayvolt/scooters/{id}/telemetry` | simulator | backend | no | every 5 s, see below |
| `rayvolt/scooters/{id}/events` | simulator | backend | no | `{"scooterId": 7, "timestamp": "...", "type": "FAULT"}` |

Telemetry:

```json
{"scooterId": 7, "timestamp": "2026-11-22T10:00:00+00:00", "battery": 100, "locked": true,
 "speedKmh": 0, "hubId": 1, "lat": 40.612, "lon": -8.7175, "status": "AT_HUB", "fault": false}
```

`status` is `AT_HUB`, `MOVING` or `OUTSIDE_HUB`. `hubId` is `null` when the scooter is not at a hub.
`fault` is `true` from a `FAULT` command until a `CLEAR_FAULT` command.

## Commands and replies

Every command has a `commandId` chosen by the backend. A command without one, or that is not JSON, is ignored with no reply.

| Command | Effect |
|---|---|
| `UNLOCK` | Starts a ride. Rejected with `SCOOTER_FAULT` (scooter has a fault), `NO_BATTERY` (0%) or `NO_OTHER_HUB` (only one hub) |
| `LOCK` | Locks the scooter where it is |
| `FAULT` | Sets the fault and sends one fault event. The ride goes on |
| `CLEAR_FAULT` | Removes the fault (technician closed the ticket) |

Reply:

```json
{"scooterId": 7, "timestamp": "...", "commandId": "...", "command": "UNLOCK", "status": "REJECTED", "reason": "SCOOTER_FAULT"}
```

`status` is `COMPLETED` or `REJECTED`, `reason` is `null` when completed. Other reasons: `UNKNOWN_SCOOTER`, `UNKNOWN_COMMAND`.
Sending `UNLOCK` to an unlocked scooter, or `LOCK` to a locked one, is `COMPLETED` and changes nothing, so the backend can retry.

## Behaviour

- The simulator reads the hubs first, then the fleet.
- `UNLOCK`: the scooter rides in a straight line to a random other hub, at 10-20 km/h, using 1% battery per 100 m.
- `LOCK`: the scooter locks where it is, also between hubs (`OUTSIDE_HUB`).
- A scooter locked at a hub charges 3% per minute, up to 100%. Locked outside a hub or unlocked, it does not charge.
- With `SEED` set, two runs with the same commands give the same speeds and destinations. Without it, every run is random.

## Load test

Register many scooters through the backend API (for example 1000), then start the simulator with `PUBLISH_INTERVAL=1`.
Every 10 s the log shows the rate of readings the broker confirmed (QoS 1), for example
`Rate: 998 readings/s (target 1000): 10000 sent, 9980 acknowledged in 10 s`.
The target is the number of scooters divided by `PUBLISH_INTERVAL`. Below 95% of the target the line starts with `Warning:`.
The first report can be low while the scooters are still arriving from the backend, so read the ones after it.

## Run and test

```
docker compose up -d broker simulator          # from the repo root
pip install -r requirements-dev.txt && pytest  # from simulator/
```

Environment: `MQTT_HOST` (default `localhost`), `MQTT_PORT` (`1883`), `PUBLISH_INTERVAL` (`5`), `CHARGE_PER_MINUTE` (`3`), `SEED` (empty).
