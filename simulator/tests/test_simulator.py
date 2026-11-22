from datetime import datetime, timezone

from simulator import Fleet, Scooter, scooter_id_from_topic


def test_id_comes_from_the_last_part_of_the_topic():
    assert scooter_id_from_topic("rayvolt/fleet/7") == 7


def test_non_numeric_id_is_rejected():
    assert scooter_id_from_topic("rayvolt/fleet/abc") is None


def test_new_scooter_starts_parked_and_full():
    scooter = Scooter(7)
    now = datetime(2026, 11, 22, 10, 0, tzinfo=timezone.utc)

    assert scooter.telemetry_topic() == "rayvolt/scooters/7/telemetry"
    assert scooter.telemetry(now) == {
        "scooterId": 7,
        "timestamp": "2026-11-22T10:00:00+00:00",
        "battery": 100,
        "locked": True,
        "speedKmh": 0,
    }


def test_fleet_message_adds_a_scooter():
    fleet = Fleet()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"id":7}')

    assert [s.id for s in fleet.scooters()] == [7]


def test_same_scooter_twice_keeps_its_state():
    fleet = Fleet()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"id":7}')
    fleet.scooters()[0].battery = 40
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"id":7}')

    assert len(fleet.scooters()) == 1
    assert fleet.scooters()[0].battery == 40


def test_empty_message_removes_the_scooter():
    fleet = Fleet()
    fleet.handle_fleet_message("rayvolt/fleet/7", b'{"id":7}')
    fleet.handle_fleet_message("rayvolt/fleet/7", b"")

    assert fleet.scooters() == []


def test_invalid_topic_is_ignored():
    fleet = Fleet()
    fleet.handle_fleet_message("rayvolt/fleet/abc", b'{"id":"abc"}')

    assert fleet.scooters() == []
