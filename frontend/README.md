# RayVolt frontend

The frontend is a React + TypeScript application built with Vite.

## Run locally

```bash
npm ci
npm run dev
```

The development server proxies `/api` requests to `http://localhost:8080`. Open `/availability` (linked from the home page and navigation) to load hubs from `GET /api/hubs` and search `GET /api/scooters/available?hubId=<id>&minBatteryPercent=10`, optionally adding `type=Standard`, `type=Cargo`, or `type=Patrol`.

The availability response contains scooter IDs, types, hub IDs, statuses, and battery percentages. Only `AVAILABLE` scooters at the selected hub with at least 10% battery are returned. The threshold is inclusive. Role-based filtering is blocked until authentication and authorization are implemented by PT-9, PT-10, PT-11, and PT-24; these stories are currently To Do.

The role/type mapping agreed for a future authenticated flow is Worker → Standard or Cargo, Security Guard → Patrol, and Contractor → Standard. This mapping is not enforced by this story because the backend does not yet provide an authenticated user role.

Battery percentage is persisted by the backend. The telemetry simulator is not yet connected to update this field, so the value shown is the latest value stored in the database, not a live reading.

## Validate

```bash
npm run build
npm run lint
```

The browser-to-API availability flow is covered by `ScooterAvailabilityIT` in the backend. Build the frontend first, then run `cd backend && ./mvnw -Pe2e -Dit.test=ScooterAvailabilityIT verify` (requires Docker and the Playwright Chromium browser).
