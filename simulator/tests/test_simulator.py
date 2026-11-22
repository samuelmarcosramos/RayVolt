from datetime import datetime, timezone

from simulator import Fleet, Scooter, scooter_id_from_topic

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
