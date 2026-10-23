# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then \
      NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca npm ci --include=dev --no-audit --no-fund; \
    else npm ci --include=dev --no-audit --no-fund; fi
COPY . .
RUN npm run build

FROM nginx:1.28-alpine AS runtime
COPY docker/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=4 \
  CMD wget -Y off -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
