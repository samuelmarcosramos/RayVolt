# RayVolt frontend

The frontend is a React + TypeScript application built with Vite.

## Run locally

```bash
npm ci
npm run dev
```

The development server proxies `/api` requests to `http://localhost:8080`. Open `/availability` (linked from the navigation) to load hubs from `GET /api/hubs` and search `GET /api/scooters/available?hubId=<id>`, optionally adding `type=Standard`, `type=Cargo`, or `type=Patrol`.

The availability response contains scooter IDs, types, hub IDs, statuses, and the latest battery percentage from `scooter_state`; it shows `Unknown` until the first telemetry reading. Only `AVAILABLE` scooters at the selected hub with at least 10% battery are returned. The inclusive 10% threshold is enforced by the backend. Role-based filtering is blocked until authentication and authorization are implemented by PT-9, PT-10, PT-11, and PT-24; these stories are currently To Do.

## Validate

```bash
npm run build
npm run lint
```

The browser-to-API availability flow is covered by `ScooterAvailabilityIT` in the backend. Build the frontend first, then run `cd backend && ./mvnw -Pe2e -Dit.test=ScooterAvailabilityIT verify` (requires Docker and the Playwright Chromium browser).
