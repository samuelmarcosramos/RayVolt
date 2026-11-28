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

PT-16 still needs to create trips and capture their initial odometer and applicable
pricing. User authentication and trip ownership checks depend on the Users stories.
The end operation is available in Swagger at `/api/docs`; the PT-17 Cucumber scenarios
run with `cd backend && ./mvnw verify` against PostgreSQL.
