#!/bin/bash

# Deployment Script for SkyHigh Core
# This script deploys the application to EC2 using Docker Compose

set -e

echo "=========================================="
echo "SkyHigh Core - Deployment Script"
echo "=========================================="

# Configuration
APP_DIR="/opt/skyhigh"
DOCKER_COMPOSE_FILE="docker-compose.yml"
BACKUP_DIR="/opt/skyhigh/backups"

# Check if running on EC2
if [ ! -d "$APP_DIR" ]; then
    echo "Error: Application directory $APP_DIR not found"
    echo "Please run setup-ec2.sh first"
    exit 1
fi

cd $APP_DIR

# Create backup directory
mkdir -p $BACKUP_DIR

# Backup database (if exists)
echo "Creating database backup..."
if docker ps | grep -q skyhigh-postgres; then
    BACKUP_FILE="$BACKUP_DIR/db_backup_$(date +%Y%m%d_%H%M%S).sql"
    docker exec skyhigh-postgres pg_dump -U skyhigh skyhigh > $BACKUP_FILE
    echo "Database backed up to: $BACKUP_FILE"
    
    # Keep only last 7 backups
    ls -t $BACKUP_DIR/db_backup_*.sql | tail -n +8 | xargs -r rm
fi

# Pull latest code (if using git)
if [ -d ".git" ]; then
    echo "Pulling latest code..."
    git pull origin main
fi

# Pull latest Docker images
echo "Pulling latest Docker images..."
docker-compose pull

# Stop and remove old containers
echo "Stopping old containers..."
docker-compose down

# Start new containers
echo "Starting new containers..."
docker-compose up -d

# Wait for services to be healthy
echo "Waiting for services to start..."
sleep 30

# Check health
echo "Checking service health..."
if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✓ Backend is healthy"
else
    echo "✗ Backend health check failed"
    docker-compose logs backend
    exit 1
fi

# Clean up old Docker resources
echo "Cleaning up old Docker resources..."
docker system prune -f

echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Services status:"
docker-compose ps
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop services: docker-compose down"
echo ""
