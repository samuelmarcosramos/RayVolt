# RayVolt

> Valdren Works gets e-scooters for its workers and guards without owning a fleet: RayVolt supplies, maintains and runs them.

## Project abstract

RayVolt is an e-scooter service for Valdren Works, a fictional industrial complex so large that walking between
departments wastes time. Valdren Works does not want to own or maintain a fleet, so it hires RayVolt, a fictional
operator, under a contract with agreed service levels (SLAs). RayVolt supplies the scooters and takes care of
maintenance, battery management and moving scooters between hubs.

Workers find scooters at hubs next to departments and gates, book one or start a trip right away, and end the trip
at a hub. Security guards use them for their patrol rounds and get priority when scooters are short. Valdren Works sets
the rules of use, such as how many scooters each department may book per shift, speed limits near hazard areas and
which workers may use which scooter types, and follows the service through KPIs and SLA compliance. Contractors and
visitors pay a deposit, and anyone who goes over the allowed time or damages a scooter is charged.

**Concretization:** service pool (client: the company that owns the industrial complex; usage: work trips and
security patrols between hubs inside the complex).

## Project team

| Name | Student no. | Role |
|---|---|---|
| Samuel Ramos | 124955 | Team Coordinator |
| Francisco Santos | 126255 | Product Owner |
| Kelvin Loforte | 121420 | QA Engineer |
| Mario Santos | 127560 | DevOps Master |

Every member is also a developer.

## Project bookmarks

| What | Link |
|---|---|
| Jira backlog | <https://ua-team-e2msb3m6.atlassian.net/jira/software/projects/PT/boards/2/backlog> |
| Related repositories | none |
| API documentation | Swagger/OpenAPI (planned) |
| Static analysis dashboard | not available yet |
| Observability dashboard | not available yet |
| Deployed application | not available yet |

## End trip API (PT-17)

`POST /api/trips/{id}/end` ends an existing active trip. No request body is needed:
position, hub, fault and distance come from scooter telemetry. The response contains
`endedAt`, `endHubId` (null outside a hub), `endLat`, `endLon`, `kmRidden`,
`overtimeMinutes`, `overtimeCharge` and `noHubCharge` (EUR).
Unknown trips return 404; repeated ends, unavailable ride telemetry and inconsistent
scooter states return 409. Concurrent end requests are serialized per scooter.

The scooter becomes available, or enters maintenance when telemetry reports a fault.
At a hub its fleet hub is updated to the return hub. Outside a hub its last fleet hub
is retained; telemetry continues to show its actual position. A LOCK command is sent
through the existing command service after commit; physical lock confirmation arrives
asynchronously through MQTT. A broker failure or timeout is logged by that service.

Trips store the maximum duration and overtime rate supplied at start. Overtime applies
per started minute after the maximum plus five minutes; exactly at the limit costs zero.
Ending outside a hub adds EUR 5.00. Both charges can apply together. These amounts are
recorded on the trip; payment collection belongs to the payments stories.

Trip start captures the initial odometer and applicable pricing (see below).
User authentication and trip ownership checks at end depend on the Users stories.
The end operation is available in Swagger at `/api/docs`; the PT-17 Cucumber scenarios
run with `cd backend && ./mvnw verify` against PostgreSQL.

## Start trip API (PT-16)

`POST /api/trips` accepts `{"scooterId": 7}` and returns the created trip with HTTP 201.
The authenticated servlet principal name must contain the persisted rider ID. The
client cannot supply the rider category, prices, permitted types or deposit result.
Until Users supplies an authenticated principal, anonymous requests return 401.

Start checks the persisted rider account, policy and scooter telemetry. The scooter
must be available or reserved by this rider, locked at its assigned hub and without
a fault. Missing telemetry blocks start. Battery exactly at the policy minimum is
allowed. An active trip or booking for another scooter blocks another start.
Bookings expire at their exact `expiresAt` instant and are consumed on a successful start.

Policies use rider categories (worker, security guard, contractor, visitor), separately
from application access roles such as admin or operator. Each category has allowed
scooter types, hours, an IANA time zone, a minimum battery, maximum minutes and overtime
rate. Opening is inclusive and closing exclusive; overnight windows are supported.
Equal opening and closing times mean 24-hour access. External accounts require an end
date and remain valid through that date in the policy's time zone. No policy or price
is invented when configuration is absent: start returns 403 until a policy exists.

The `billing` package provides a local, persistent payment sandbox. Contractors and
visitors need an account with at least EUR 20.00 available. Start deducts that available
amount and creates a hold linked to the trip; workers and guards do not need a hold.
The hold, trip, reservation consumption and scooter state change share one database
transaction. A failure rolls them back together, and UNLOCK is requested through the
existing command service only after commit. Physical confirmation remains asynchronous.
Deposit release and settlement belong to PT-55 and are not performed by this start endpoint.

Migration V7 adds the minimal rider, policy, reservation and sandbox tables. Users,
Bookings and Backoffice will manage these records through their own stories; this PR
does not add account-management or policy-editing endpoints. Rider and scooter locks
serialize concurrent starts so one rider or scooter cannot acquire two active trips.

Example synthetic local setup (sample policy values, not project defaults):

```sql
INSERT INTO trip_riders (id, category, enabled) VALUES (8101, 'WORKER', true);
INSERT INTO trip_policies
    (category, allowed_from, allowed_until, time_zone, minimum_battery, max_minutes, overtime_rate)
VALUES ('WORKER', '08:00', '18:00', 'Europe/Lisbon', 10, 30, 0.50);
INSERT INTO trip_policy_types (category, scooter_type) VALUES ('WORKER', 'standard');
```

HTTP responses: 400 for an invalid scooter ID, 401 without a valid authenticated
identity, 403 for missing/expired/disabled riders or policy restrictions, 404 for an
unknown scooter, and 409 for unavailable scooters, low battery or a refused deposit.
Run `cd backend && ./mvnw verify` for unit, HTTP and PostgreSQL Cucumber tests tagged
`@PT-16`, including concurrent requests and deposit refusal.

## Booking API (PT-15)

`POST /api/bookings` accepts `{"scooterId": 7}` and returns HTTP 201 with `scooterId`,
`riderId` and `expiresAt`. The authenticated principal identifies the rider, using the
same contract as trip start. The server sets expiry to exactly ten minutes after
creation. A rider may have only one active booking or trip. The scooter must be
available, locked at its hub and eligible under the persisted account, type, hours
and battery rules. Booking does not unlock the scooter or hold a deposit.

`DELETE /api/bookings/{scooterId}` cancels the authenticated owner's current booking
before expiry and returns HTTP 204. Cancellation is free and returns a reserved
scooter to available. Another rider receives 403, an absent booking 404, and an
expired booking 409. Invalid creation requests return 400, unauthenticated requests
401, policy restrictions 403 and unavailable scooters or existing active use 409.

Validity ends exactly at `expiresAt`; a booking is inactive at that instant. The
expiry job checks persisted bookings every second by default, so the stored scooter
state is released on the next sweep. A request to reserve that same scooter can
also replace an expired booking immediately, without waiting for the job. The job
catches up after an application restart and rechecks each booking under the scooter
lock, in its own transaction. Delayed checks cannot delete a replacement booking or
undo a trip that has consumed the booking. Expiry records the SYSTEM state-change
cause. Cancellation and expiry preserve maintenance or retired states.

The existing rider-then-scooter lock order also serializes booking against trip start.
State changes publish fleet updates after commit. The expiry job is enabled by
default; `rayvolt.bookings.expiry-enabled=false` disables it and
`rayvolt.bookings.expiry-interval` changes the interval in milliseconds (default 1000).
Tests disable background scheduling and advance an injected clock explicitly.

Run `cd backend && ./mvnw verify` for the `@PT-15` Cucumber scenarios and unit/HTTP
tests. They cover exact expiry boundaries, free cancellation, ownership, active-use
limits, concurrent requests, delayed cleanup and maintenance after telemetry faults.
This branch depends on PT-16's persisted riders, policies and reservation support.
