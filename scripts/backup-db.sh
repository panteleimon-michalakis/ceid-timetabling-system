#!/usr/bin/env bash
# Backup της βάσης ceid_timetable (Linux/macOS/WSL ή μέσα από docker)
# Χρήση: DB_PASSWORD=... ./scripts/backup-db.sh
set -euo pipefail
BACKUP_DIR="$(dirname "$0")/../backups"
mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y-%m-%d_%H%M)
OUT="$BACKUP_DIR/ceid_timetable_$STAMP.dump"
PGPASSWORD="${DB_PASSWORD:?Ορίστε DB_PASSWORD}" pg_dump -U ceid_admin -h localhost -d ceid_timetable -F c -f "$OUT"
echo "OK: $OUT"
