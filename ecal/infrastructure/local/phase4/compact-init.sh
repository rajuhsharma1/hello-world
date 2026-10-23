#!/bin/sh
# Optional compact lab: one physical PostgreSQL instance, four ordinary database owners.
# Bootstrap credentials stay in this container; applications receive only their own role.
set -eu
for owner in orders inventory payment reporting; do
  case "$owner" in
    orders) secret="$ORDERS_PASSWORD" ;;
    inventory) secret="$INVENTORY_PASSWORD" ;;
    payment) secret="$PAYMENT_PASSWORD" ;;
    reporting) secret="$REPORTING_PASSWORD" ;;
  esac
  psql --username "$POSTGRES_USER" --dbname postgres --set ON_ERROR_STOP=1 --set owner="$owner" --set secret="$secret" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'secret';
CREATE DATABASE :"owner" OWNER :"owner";
REVOKE CONNECT ON DATABASE :"owner" FROM PUBLIC;
SQL
done
