#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash restore-admin-permissions.sh [deployment-directory]

Examples:
  cd /opt/corp-idm
  bash /opt/LDAP_incremental_update/restore-admin-permissions.sh .

  bash restore-admin-permissions.sh /opt/corp-idm
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

DEPLOY_DIR="${1:-.}"

if [[ ! -d "$DEPLOY_DIR" ]]; then
  echo "ERROR: deployment directory does not exist: $DEPLOY_DIR" >&2
  exit 1
fi

DEPLOY_DIR="$(cd "$DEPLOY_DIR" && pwd)"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

if [[ ! -f "$ENV_FILE" || ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: .env or docker-compose.yml is missing in: $DEPLOY_DIR" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker command was not found" >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")

echo "[1/7] Validating the existing Docker Compose deployment..."
"${COMPOSE[@]}" config --quiet

mysql_query() {
  local sql="$1"
  "${COMPOSE[@]}" exec -T mysql sh -c \
    'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$MYSQL_DATABASE"' <<<"$sql"
}

echo "[2/7] Checking the admin account and SUPER_ADMIN role..."
ADMIN_COUNT="$(mysql_query "SELECT COUNT(*) FROM sys_user WHERE user_id = 'admin' AND deleted = 0;")"
SUPER_ADMIN_COUNT="$(mysql_query "SELECT COUNT(*) FROM sys_role WHERE role_code = 'SUPER_ADMIN';")"
ACTIVE_PERMISSION_COUNT="$(mysql_query "SELECT COUNT(*) FROM sys_permission WHERE status = 1;")"

if [[ "$ADMIN_COUNT" != "1" ]]; then
  echo "ERROR: expected exactly one active admin account, found: $ADMIN_COUNT" >&2
  exit 1
fi

if [[ "$SUPER_ADMIN_COUNT" != "1" ]]; then
  echo "ERROR: expected exactly one SUPER_ADMIN role, found: $SUPER_ADMIN_COUNT" >&2
  exit 1
fi

if ! [[ "$ACTIVE_PERMISSION_COUNT" =~ ^[0-9]+$ ]] || (( ACTIVE_PERMISSION_COUNT == 0 )); then
  echo "ERROR: no active permissions were found" >&2
  exit 1
fi

TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
BACKUP_DIR="$DEPLOY_DIR/backups/admin-permission-recovery-$TIMESTAMP"
BACKUP_FILE="$BACKUP_DIR/rbac-before-recovery.sql"

echo "[3/7] Backing up the affected RBAC tables..."
mkdir -p "$BACKUP_DIR"
"${COMPOSE[@]}" exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces "$MYSQL_DATABASE" sys_user sys_role sys_permission sys_user_role sys_role_permission' \
  >"$BACKUP_FILE"

if [[ ! -s "$BACKUP_FILE" ]]; then
  echo "ERROR: backup file is empty: $BACKUP_FILE" >&2
  exit 1
fi

echo "[4/7] Restoring admin role binding and all active permissions..."
"${COMPOSE[@]}" exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' <<'SQL'
START TRANSACTION;

UPDATE sys_user
SET access_allowed = 1,
    employment_status = 'ACTIVE',
    modifier = 'system:admin-recovery',
    gmt_modified = CURRENT_TIMESTAMP
WHERE user_id = 'admin'
  AND deleted = 0;

UPDATE sys_role
SET permission_level = 1,
    built_in = 1,
    status = 1,
    role_scope = 'SYSTEM',
    role_group_id = NULL,
    modifier = 'system:admin-recovery',
    gmt_modified = CURRENT_TIMESTAMP
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO sys_user_role (user_id, role_id, creator)
SELECT user_account.id, role.id, 'system:admin-recovery'
FROM sys_user user_account
INNER JOIN sys_role role ON role.role_code = 'SUPER_ADMIN'
LEFT JOIN sys_user_role binding
  ON binding.user_id = user_account.id
 AND binding.role_id = role.id
WHERE user_account.user_id = 'admin'
  AND user_account.deleted = 0
  AND binding.user_id IS NULL;

INSERT INTO sys_role_permission (role_id, permission_id, creator)
SELECT role.id, permission.id, 'system:admin-recovery'
FROM sys_role role
INNER JOIN sys_permission permission ON permission.status = 1
LEFT JOIN sys_role_permission binding
  ON binding.role_id = role.id
 AND binding.permission_id = permission.id
WHERE role.role_code = 'SUPER_ADMIN'
  AND binding.role_id IS NULL;

INSERT INTO sys_audit_log (
    operator, operation_type, biz_type, biz_id, after_json, result, gmt_create
)
SELECT
    'system:admin-recovery',
    'ADMIN_PERMISSION_RECOVERY',
    'USER',
    CAST(user_account.id AS CHAR),
    JSON_OBJECT('userId', 'admin', 'roleCode', 'SUPER_ADMIN'),
    'SUCCESS',
    CURRENT_TIMESTAMP
FROM sys_user user_account
WHERE user_account.user_id = 'admin'
  AND user_account.deleted = 0;

COMMIT;
SQL

echo "[5/7] Verifying the restored database state..."
ADMIN_SUPER_BINDING_COUNT="$(mysql_query "
SELECT COUNT(*)
FROM sys_user user_account
INNER JOIN sys_user_role user_role ON user_role.user_id = user_account.id
INNER JOIN sys_role role ON role.id = user_role.role_id
WHERE user_account.user_id = 'admin'
  AND user_account.deleted = 0
  AND user_account.access_allowed = 1
  AND user_account.employment_status = 'ACTIVE'
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.built_in = 1;")"

ASSIGNED_PERMISSION_COUNT="$(mysql_query "
SELECT COUNT(DISTINCT role_permission.permission_id)
FROM sys_role role
INNER JOIN sys_role_permission role_permission ON role_permission.role_id = role.id
INNER JOIN sys_permission permission
  ON permission.id = role_permission.permission_id
 AND permission.status = 1
WHERE role.role_code = 'SUPER_ADMIN';")"

if [[ "$ADMIN_SUPER_BINDING_COUNT" != "1" ]]; then
  echo "ERROR: admin still does not have an active SUPER_ADMIN binding" >&2
  exit 1
fi

if [[ "$ASSIGNED_PERMISSION_COUNT" != "$ACTIVE_PERMISSION_COUNT" ]]; then
  echo "ERROR: permission count mismatch: assigned=$ASSIGNED_PERMISSION_COUNT active=$ACTIVE_PERMISSION_COUNT" >&2
  exit 1
fi

echo "[6/7] Restarting only idm-app to reload Casbin policies..."
"${COMPOSE[@]}" restart idm-app

echo "[7/7] Waiting for the backend health check..."
HEALTHY=0
for _ in $(seq 1 24); do
  if "${COMPOSE[@]}" exec -T idm-app sh -c \
    'curl -fsS "http://127.0.0.1:${SERVER_PORT:-8081}/actuator/health"' >/dev/null 2>&1; then
    HEALTHY=1
    break
  fi
  sleep 5
done

if [[ "$HEALTHY" != "1" ]]; then
  echo "ERROR: idm-app did not become healthy; inspect it with:" >&2
  echo "  docker compose --env-file '$ENV_FILE' -f '$COMPOSE_FILE' logs --tail=200 idm-app" >&2
  echo "The database backup is: $BACKUP_FILE" >&2
  exit 1
fi

echo
echo "Admin permission recovery completed successfully."
echo "Active permissions assigned: $ASSIGNED_PERMISSION_COUNT"
echo "Backup file: $BACKUP_FILE"
echo "Sign in again with the existing admin password."
