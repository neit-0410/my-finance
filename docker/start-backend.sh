#!/bin/sh
set -eu
if [ -z "${FINANCE_JWT_SECRET:-}" ]; then
  echo 'FINANCE_JWT_SECRET is required. Configure .env.docker using the Docker guide.' >&2
  exit 1
fi
exec java -jar /app/app.jar
