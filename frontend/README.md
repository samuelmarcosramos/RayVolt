# RayVolt frontend

The frontend is a React + TypeScript application built with Vite.

## Run locally

```bash
npm ci
npm run dev
```

The development server proxies `/api` requests to `http://localhost:8080`. The home page checks `GET /api/health` and expects a JSON response with a string `status` property, for example `{"status":"UP"}`. Set `VITE_API_HEALTH_URL` to override the health endpoint.

## Validate

```bash
npm run build
```
