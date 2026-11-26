# RayVolt frontend

The frontend is a React + TypeScript application built with Vite.

## Run locally

```bash
npm ci
npm run dev
```

The development server proxies `/api` requests to `http://localhost:8080`. The home page loads hubs from `GET /api/hubs` and searches `GET /api/scooters/available?hubId=<id>&minBatteryPercent=10`, optionally adding `type=Standard`, `type=Cargo`, or `type=Patrol`.

The availability response contains scooter IDs, types, hub IDs, statuses, and battery percentages. Only `AVAILABLE` scooters at the selected hub with at least 10% battery are returned. The threshold is inclusive. Role-based filtering is not applied until user authentication and authorization are available.

The role/type mapping agreed for a future authenticated flow is Worker → Standard or Cargo, Security Guard → Patrol, and Contractor → Standard. This mapping is not enforced by this story because the backend does not yet provide an authenticated user role.

Battery percentage is persisted by the backend. The telemetry simulator is not yet connected to update this field, so the value shown is the latest value stored in the database, not a live reading.

## Validate

```bash
npm test
npm run build
```
