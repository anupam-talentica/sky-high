#!/bin/bash

# EC2 Setup Script for SkyHigh Core
# This script sets up Docker and Docker Compose on an Amazon Linux 2023 EC2 instance

set -e

echo "=========================================="
echo "SkyHigh Core - EC2 Setup Script"
echo "=========================================="

# Update system packages
echo "Updating system packages..."
sudo yum update -y

# Install Docker
echo "Installing Docker..."
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker

# Add ec2-user to docker group
echo "Adding user to docker group..."
sudo usermod -a -G docker ec2-user

# Install Docker Compose
echo "Installing Docker Compose..."
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Verify installations
echo "Verifying installations..."
docker --version
docker-compose --version

# Install CloudWatch Agent
echo "Installing CloudWatch Agent..."
sudo yum install -y amazon-cloudwatch-agent

# Create application directory
echo "Creating application directory..."
sudo mkdir -p /opt/skyhigh
sudo chown ec2-user:ec2-user /opt/skyhigh

# Install Git
echo "Installing Git..."
sudo yum install -y git

# Install wget and curl
echo "Installing wget and curl..."
sudo yum install -y wget curl

# Configure firewall (if needed)
echo "Configuring firewall..."
sudo systemctl stop firewalld || true
sudo systemctl disable firewalld || true

# Create log directory
echo "Creating log directory..."
sudo mkdir -p /var/log/skyhigh
sudo chown ec2-user:ec2-user /var/log/skyhigh

echo "=========================================="
echo "EC2 Setup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Log out and log back in for docker group changes to take effect"
echo "2. Clone the repository to /opt/skyhigh"
echo "3. Create .env file with production credentials"
echo "4. Run: docker-compose up -d"
echo ""
