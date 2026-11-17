# Build stage: build the static site with Vite
FROM node:24-alpine AS build
WORKDIR /app

# Install dependencies first so they are cached when only src changes
COPY package.json package-lock.json ./
RUN npm ci

COPY index.html vite.config.ts tsconfig*.json ./
COPY public public
COPY src src
RUN npm run build

# Run stage: nginx serves the site and forwards /api to the backend
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
