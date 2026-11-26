# Hubs — PT-33

`GET /api/hubs` lists all hubs ordered by name; `GET /api/hubs/{id}` reads one.
`POST /api/hubs` creates a hub (201, `Location: /api/hubs/{id}`).
`PUT /api/hubs/{id}` replaces its editable details (200).

Request example:

```json
{"name":"North Gate","department":"Security","capacity":3,"lat":40.6,"lon":-8.7}
```

Responses contain these fields plus `id`. Names and departments are trimmed and
must have 1–120 characters. Names are unique (case-sensitive). Capacity is an integer
greater than zero. Both coordinates are required: latitude -90…90, longitude -180…180.
Edits cannot reduce capacity below the count of non-retired scooters assigned to the
hub, matching the fleet module's definition of occupancy. Equal capacity is allowed.

Errors use Problem Details: 400 invalid input, 404 unknown hub, 409 duplicate name or
insufficient capacity. A unique database constraint protects concurrent creation;
updates lock the hub row, using the same locking protocol as scooter additions/moves.

In the web app, select **View hubs**, then add or edit a hub. Successful changes
appear immediately; other users can select **Refresh hubs** to reload the list.
As with the fleet endpoints, operator write authorization awaits the Users module.
Existing seeded hubs get their name as department; legacy inserts default to
`Unassigned`. Historical zero-capacity/unknown-location hubs remain readable;
all new or edited hubs must satisfy the input rules above.

## Acceptance criteria → tests

| Criterion | Automated evidence |
|---|---|
| Create with name, department, capacity and location | `HubControllerTest`, `HubServiceTest`, `HubSteps`, `Hubs.test.tsx` |
| Unique name, capacity > 0 | `HubDetailsTest`, `HubServiceTest`, `HubRepositoryContract`, Cucumber scenarios |
| Edit; capacity >= current scooters | `HubServiceTest.editChecksBelowEqualAndAboveOccupancy`, `HubRepositoryContract`, Cucumber scenarios |
| New hubs appear in user list | `HubRepositoryContract.createdAndEditedHubPersistsAndAppearsInUserList`, Cucumber, `Hubs.test.tsx` |

Run `cd backend && ./mvnw test` and `./mvnw verify` (Docker required for PostgreSQL,
MQTT and Cucumber integration tests). Run `cd frontend && npm ci && npm test && npm run build`.
Cucumber JSON is written to `backend/target/cucumber/PT-33.json` for Xray.
