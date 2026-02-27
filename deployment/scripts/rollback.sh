#!/bin/bash

# Rollback Script for SkyHigh Core
# This script rolls back to a previous database backup

set -e

echo "=========================================="
echo "SkyHigh Core - Rollback Script"
echo "=========================================="

APP_DIR="/opt/skyhigh"
BACKUP_DIR="/opt/skyhigh/backups"

cd $APP_DIR

# List available backups
echo "Available backups:"
ls -lh $BACKUP_DIR/db_backup_*.sql 2>/dev/null || {
    echo "No backups found in $BACKUP_DIR"
    exit 1
}

# Prompt for backup file
echo ""
read -p "Enter the backup filename to restore: " BACKUP_FILE

if [ ! -f "$BACKUP_DIR/$BACKUP_FILE" ]; then
    echo "Error: Backup file not found: $BACKUP_DIR/$BACKUP_FILE"
    exit 1
fi

# Confirm rollback
echo ""
echo "WARNING: This will restore the database to the state in $BACKUP_FILE"
read -p "Are you sure you want to continue? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Rollback cancelled"
    exit 0
fi

# Stop backend
echo "Stopping backend..."
docker-compose stop backend

# Restore database
echo "Restoring database..."
docker exec -i skyhigh-postgres psql -U skyhigh -d skyhigh < "$BACKUP_DIR/$BACKUP_FILE"

# Restart backend
echo "Restarting backend..."
docker-compose up -d backend

# Wait for service to be healthy
echo "Waiting for backend to start..."
sleep 30

# Check health
if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✓ Rollback successful - Backend is healthy"
else
    echo "✗ Rollback may have issues - Backend health check failed"
    docker-compose logs backend
    exit 1
fi

echo "=========================================="
echo "Rollback Complete!"
echo "=========================================="
