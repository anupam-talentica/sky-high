#!/bin/bash

# AWS Infrastructure Deployment Script
# This script deploys the CloudFormation stack for SkyHigh Core

set -e

echo "=========================================="
echo "SkyHigh Core - AWS Infrastructure Deployment"
echo "=========================================="

# Configuration
STACK_NAME="skyhigh-core-stack"
TEMPLATE_FILE="cloudformation-template.yml"
REGION="us-east-1"

# Prompt for parameters
read -p "Enter your EC2 Key Pair name: " KEY_PAIR_NAME
read -p "Enter your IP address for SSH access (CIDR format, e.g., 1.2.3.4/32): " ALLOWED_SSH_IP
read -p "Enter environment name (dev/prod) [prod]: " ENV_NAME
ENV_NAME=${ENV_NAME:-prod}

echo ""
echo "Deploying CloudFormation stack..."
echo "Stack Name: $STACK_NAME"
echo "Region: $REGION"
echo "Environment: $ENV_NAME"
echo ""

# Deploy stack
aws cloudformation deploy \
  --template-file $TEMPLATE_FILE \
  --stack-name $STACK_NAME \
  --parameter-overrides \
    KeyPairName=$KEY_PAIR_NAME \
    AllowedSSHIP=$ALLOWED_SSH_IP \
    EnvironmentName=$ENV_NAME \
  --capabilities CAPABILITY_NAMED_IAM \
  --region $REGION

# Get outputs
echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Stack Outputs:"
aws cloudformation describe-stacks \
  --stack-name $STACK_NAME \
  --region $REGION \
  --query 'Stacks[0].Outputs' \
  --output table

echo ""
echo "Next steps:"
echo "1. SSH into EC2 instance using the public IP"
echo "2. Clone the repository to /opt/skyhigh"
echo "3. Create .env file with production credentials"
echo "4. Run: docker-compose up -d"
echo ""
