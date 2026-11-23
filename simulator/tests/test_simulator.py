import json
import random
from datetime import datetime, timezone

from simulator import Fleet, RateMeter, Scooter, distance_m, scooter_id_from_topic

NOW = datetime(2026, 11, 22, 10, 0, tzinfo=timezone.utc)
HUBS = b'[{"id": 1, "name": "Main Gate", "lat": 40.612, "lon": -8.7175},' \
       b' {"id": 2, "name": "Production Hall", "lat": 40.606, "lon": -8.72}]'


def fleet_with_hubs():
    fleet = Fleet()
    fleet.handle_hubs_message(HUBS)
    return fleet


def test_id_comes_from_the_last_part_of_the_topic():
    assert scooter_id_from_topic("rayvolt/fleet/7") == 7


def test_non_numeric_id_is_rejected():
    assert scooter_id_from_topic("rayvolt/fleet/abc") is None


def test_new_scooter_starts_parked_and_full_at_its_hub():
    scooter = Scooter(7, {"id": 2, "name": "Production Hall", "lat": 40.606, "lon": -8.72})
    now = datetime(2026, 11, 22, 10, 0, tzinfo=timezone.utc)

    assert scooter.telemetry_topic() == "rayvolt/scooters/7/telemetry"
    assert scooter.telemetry(now) == {
        "scooterId": 7,
        "timestamp": "2026-11-22T10:00:00+00:00",
        "battery": 100,
        "locked": True,
        "speedKmh": 0,
        "hubId": 2,
        "lat": 40.606,
        "lon": -8.72,
        "status": "AT_HUB",
        "fault": False,
    }


def test_hubs_message_is_read():
    fleet = fleet_with_hubs()

    assert sorted(fleet.hubs) == [1, 2]
    assert fleet.hubs[2]["lat"] == 40.606


def test_invalid_hubs_message_keeps_the_old_hubs():
    fleet = fleet_with_hubs()
    fleet.handle_hubs_message(b"not json")

    assert sorted(fleet.hubs) == [1, 2]


def test_fleet_message_adds_a_scooter_at_its_hub():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 2}')

    scooter = fleet.scooters()[0]
    assert (scooter.id, scooter.hub_id, scooter.lat, scooter.lon) == (7, 2, 40.606, -8.72)


def test_scooter_with_unknown_hub_is_ignored():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 99}')

    assert fleet.scooters() == []


def test_scooter_without_hub_is_ignored():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"id": 7}')

    assert fleet.scooters() == []


def test_same_scooter_twice_keeps_its_state():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 2}')
    fleet.scooters()[0].battery = 40
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 2}')

    assert len(fleet.scooters()) == 1
    assert fleet.scooters()[0].battery == 40


def test_retired_scooter_is_removed():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 2}')
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 2, "status": "RETIRED"}')

    assert fleet.scooters() == []


def test_empty_message_keeps_the_scooter():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 2}')
    fleet.handle_fleet_message("rayvolt/fleet/7", b"")

    assert [s.id for s in fleet.scooters()] == [7]


def test_invalid_topic_is_ignored():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/abc", b'{"hubId": 2}')

    assert fleet.scooters() == []


MAIN_GATE = {"id": 1, "name": "Main Gate", "lat": 40.612, "lon": -8.7175}
PRODUCTION = {"id": 2, "name": "Production Hall", "lat": 40.606, "lon": -8.72}


class FixedSpeed:
    def __init__(self, speed):
        self.speed = speed

    def uniform(self, low, high):
        return self.speed

    def choice(self, options):
        return options[0]


def moving_scooter():
    scooter = Scooter(7, MAIN_GATE)
    scooter.unlock([MAIN_GATE, PRODUCTION])
    return scooter


def test_command_topic_gives_the_scooter_id():
    assert scooter_id_from_topic("rayvolt/scooters/7/commands") == 7


def test_unlock_goes_to_another_hub():
    scooter = moving_scooter()

    assert scooter.destination == PRODUCTION
    assert (scooter.locked, scooter.hub_id, scooter.status) == (False, 1, "MOVING")


def test_unlock_with_only_one_hub_stays_locked():
    scooter = Scooter(7, MAIN_GATE)
    scooter.unlock([MAIN_GATE])

    assert scooter.locked
    assert scooter.status == "AT_HUB"


def test_step_moves_towards_the_destination_and_drains_battery():
    scooter = moving_scooter()
    start = distance_m(scooter.lat, scooter.lon, PRODUCTION["lat"], PRODUCTION["lon"])

    scooter.step(10, FixedSpeed(18))

    left = distance_m(scooter.lat, scooter.lon, PRODUCTION["lat"], PRODUCTION["lon"])
    assert round(start - left) == 50
    assert scooter.battery == 99.5
    assert scooter.speed_kmh == 18


def test_speed_is_between_10_and_20():
    scooter = moving_scooter()
    for _ in range(50):
        scooter.step(1)
        assert 10 <= scooter.speed_kmh <= 20


def test_scooter_stops_at_the_destination_hub():
    scooter = moving_scooter()

    scooter.step(3600, FixedSpeed(18))

    assert (scooter.lat, scooter.lon) == (PRODUCTION["lat"], PRODUCTION["lon"])
    assert (scooter.hub_id, scooter.status, scooter.speed_kmh) == (2, "AT_HUB", 0)
    assert not scooter.locked


def test_lock_before_moving_stays_at_the_start_hub():
    scooter = moving_scooter()
    scooter.lock()

    assert (scooter.locked, scooter.hub_id, scooter.status) == (True, 1, "AT_HUB")


def test_lock_at_the_hub_is_at_hub():
    scooter = moving_scooter()
    scooter.step(3600)
    scooter.lock()

    assert scooter.locked
    assert scooter.status == "AT_HUB"


def test_lock_on_the_way_is_outside_hub():
    scooter = moving_scooter()
    scooter.step(10)
    scooter.lock()

    assert (scooter.locked, scooter.hub_id, scooter.status, scooter.speed_kmh) == (True, None, "OUTSIDE_HUB", 0)


def test_locked_scooter_does_not_move():
    scooter = Scooter(7, MAIN_GATE)
    scooter.step(10)

    assert (scooter.lat, scooter.lon, scooter.battery) == (MAIN_GATE["lat"], MAIN_GATE["lon"], 100)


COMMANDS = "rayvolt/scooters/7/commands"


def fleet_with_scooter():
    fleet = fleet_with_hubs()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 1}')
    return fleet


def send(fleet, command, topic=COMMANDS):
    return fleet.handle_command(topic, json.dumps({"command": command, "commandId": "c-1"}).encode(), NOW)


def reply(command, status="COMPLETED", reason=None, scooter_id=7):
    return (
        f"rayvolt/scooters/{scooter_id}/responses",
        {
            "scooterId": scooter_id,
            "timestamp": "2026-11-22T10:00:00+00:00",
            "commandId": "c-1",
            "command": command,
            "status": status,
            "reason": reason,
        },
    )


FAULT_EVENT = ("rayvolt/scooters/7/events", {"scooterId": 7, "timestamp": "2026-11-22T10:00:00+00:00", "type": "FAULT"})


def test_unlock_and_lock_are_completed():
    fleet = fleet_with_scooter()

    assert send(fleet, "UNLOCK") == [reply("UNLOCK")]
    assert fleet.scooters()[0].status == "MOVING"

    assert send(fleet, "LOCK") == [reply("LOCK")]
    assert fleet.scooters()[0].status == "AT_HUB"


def test_unlock_twice_is_completed_and_changes_nothing():
    fleet = fleet_with_scooter()
    send(fleet, "UNLOCK")
    destination = fleet.scooters()[0].destination

    assert send(fleet, "UNLOCK") == [reply("UNLOCK")]
    assert fleet.scooters()[0].destination == destination


def test_lock_twice_is_completed():
    fleet = fleet_with_scooter()

    assert send(fleet, "LOCK") == [reply("LOCK")]
    assert fleet.scooters()[0].locked


def test_command_without_command_id_gets_no_reply():
    fleet = fleet_with_scooter()

    assert fleet.handle_command(COMMANDS, b'{"command": "UNLOCK"}', NOW) == []
    assert fleet.scooters()[0].locked


def test_invalid_json_gets_no_reply():
    fleet = fleet_with_scooter()

    assert fleet.handle_command(COMMANDS, b"not json", NOW) == []


def test_unknown_scooter_is_rejected():
    fleet = fleet_with_hubs()

    assert send(fleet, "UNLOCK", "rayvolt/scooters/9/commands") == [
        reply("UNLOCK", "REJECTED", "UNKNOWN_SCOOTER", scooter_id=9)
    ]


def test_unknown_command_is_rejected():
    fleet = fleet_with_scooter()

    assert send(fleet, "JUMP") == [reply("JUMP", "REJECTED", "UNKNOWN_COMMAND")]


def test_unlock_with_empty_battery_is_rejected():
    fleet = fleet_with_scooter()
    fleet.scooters()[0].battery = 0.4

    assert send(fleet, "UNLOCK") == [reply("UNLOCK", "REJECTED", "NO_BATTERY")]
    assert fleet.scooters()[0].locked


def test_unlock_with_only_one_hub_is_rejected():
    fleet = Fleet()
    fleet.handle_hubs_message(b'[{"id": 1, "name": "Main Gate", "lat": 40.612, "lon": -8.7175}]')
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 1}')

    assert send(fleet, "UNLOCK") == [reply("UNLOCK", "REJECTED", "NO_OTHER_HUB")]


def test_fault_sends_the_event_and_a_reply():
    fleet = fleet_with_scooter()

    assert send(fleet, "FAULT") == [FAULT_EVENT, reply("FAULT")]
    assert fleet.scooters()[0].telemetry(NOW)["fault"] is True


def test_second_fault_sends_no_new_event():
    fleet = fleet_with_scooter()
    send(fleet, "FAULT")

    assert send(fleet, "FAULT") == [reply("FAULT")]


def test_fault_does_not_stop_the_ride():
    fleet = fleet_with_scooter()
    send(fleet, "UNLOCK")

    send(fleet, "FAULT")

    scooter = fleet.scooters()[0]
    assert (scooter.locked, scooter.status, scooter.destination) == (False, "MOVING", PRODUCTION)


def test_unlock_with_fault_is_rejected():
    fleet = fleet_with_scooter()
    send(fleet, "FAULT")

    assert send(fleet, "UNLOCK") == [reply("UNLOCK", "REJECTED", "SCOOTER_FAULT")]
    assert fleet.scooters()[0].locked


def test_clear_fault_allows_unlock_again():
    fleet = fleet_with_scooter()
    send(fleet, "FAULT")

    assert send(fleet, "CLEAR_FAULT") == [reply("CLEAR_FAULT")]
    assert fleet.scooters()[0].telemetry(NOW)["fault"] is False
    assert send(fleet, "UNLOCK") == [reply("UNLOCK")]


def test_battery_stops_at_100():
    scooter = Scooter(7, MAIN_GATE)
    scooter.battery = 99

    scooter.step(60)

    assert scooter.battery == 100


def test_scooter_locked_outside_hub_does_not_charge():
    scooter = moving_scooter()
    scooter.step(10)
    scooter.lock()
    battery = scooter.battery

    scooter.step(60)

    assert scooter.battery == battery


def test_unlocked_scooter_waiting_at_a_hub_does_not_charge():
    scooter = moving_scooter()
    scooter.step(3600)
    battery = scooter.battery

    scooter.step(60)

    assert scooter.battery == battery


def ride(seed):
    fleet = Fleet(random.Random(seed))
    fleet.handle_hubs_message(b'[{"id": 1, "name": "A", "lat": 40.612, "lon": -8.7175},'
                              b' {"id": 2, "name": "B", "lat": 40.606, "lon": -8.72},'
                              b' {"id": 3, "name": "C", "lat": 40.601, "lon": -8.714}]')
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"hubId": 1}')
    send(fleet, "UNLOCK")
    return [fleet.tick(5, NOW) for _ in range(5)]


def test_same_seed_gives_the_same_ride():
    assert ride("demo") == ride("demo")


def test_different_seed_gives_another_ride():
    assert ride("demo") != ride("other")


class FakeClock:
    def __init__(self):
        self.now = 0

    def __call__(self):
        return self.now


def test_rate_is_reported_every_10_seconds():
    clock = FakeClock()
    meter = RateMeter(clock=clock)

    clock.now = 5
    assert meter.add(5000) is None

    clock.now = 10
    assert meter.add(5000) == "Rate: 1000 readings/s (10000 sent in 10 s)"


def test_rate_starts_again_after_a_report():
    clock = FakeClock()
    meter = RateMeter(clock=clock)
    clock.now = 10
    meter.add(10000)

    clock.now = 20
    assert meter.add(500) == "Rate: 50 readings/s (500 sent in 10 s)"
