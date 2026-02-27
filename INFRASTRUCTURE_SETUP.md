# Infrastructure Setup Guide

This document provides step-by-step instructions for setting up the SkyHigh Core infrastructure on AWS.

## Prerequisites

- AWS Account with appropriate permissions
- AWS CLI installed and configured
- EC2 Key Pair created in your AWS region
- Docker Hub account (for CI/CD)
- GitHub repository

## Infrastructure Components

### AWS Resources

1. **VPC** (10.0.0.0/16)
   - Public subnet for EC2 instance
   - Internet Gateway for public access
   - Route tables configured

2. **EC2 Instance** (t3.medium)
   - Amazon Linux 2023
   - 2 vCPU, 4 GB RAM
   - 30 GB EBS volume (gp3, encrypted)
   - Elastic IP attached
   - Docker & Docker Compose pre-installed

3. **Security Group**
   - Port 80 (HTTP) - open to internet
   - Port 443 (HTTPS) - open to internet
   - Port 22 (SSH) - restricted to your IP

4. **S3 Bucket**
   - Frontend static files hosting
   - Versioning enabled
   - Encryption enabled (AES-256)
   - Public access blocked (CloudFront only)

5. **CloudFront Distribution**
   - CDN for frontend
   - SSL/TLS certificate
   - Custom error responses for SPA routing

6. **IAM Role**
   - EC2 instance profile
   - CloudWatch permissions
   - SSM permissions

## Deployment Steps

### Step 1: Deploy AWS Infrastructure

```bash
cd deployment/aws
./deploy-infrastructure.sh
```

This script will:
- Prompt for your EC2 Key Pair name
- Prompt for your IP address for SSH access
- Deploy the CloudFormation stack
- Display stack outputs (EC2 IP, S3 bucket, CloudFront URL)

**Note down the following outputs:**
- EC2 Public IP
- S3 Bucket Name
- CloudFront Distribution ID
- CloudFront URL

### Step 2: Configure GitHub Secrets

Add the following secrets to your GitHub repository:

**For Backend CI/CD:**
- `DOCKER_USERNAME` - Your Docker Hub username
- `DOCKER_PASSWORD` - Your Docker Hub password/token
- `EC2_HOST` - EC2 public IP address
- `EC2_USER` - `ec2-user`
- `EC2_SSH_KEY` - Your EC2 private key (entire content)

**For Frontend CI/CD:**
- `AWS_ACCESS_KEY_ID` - AWS access key
- `AWS_SECRET_ACCESS_KEY` - AWS secret key
- `S3_BUCKET_NAME` - S3 bucket name from stack output
- `CLOUDFRONT_DISTRIBUTION_ID` - CloudFront ID from stack output
- `VITE_API_BASE_URL` - Backend API URL (http://<EC2_IP>/api/v1)

### Step 3: SSH into EC2 Instance

```bash
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

### Step 4: Clone Repository

```bash
cd /opt/skyhigh
git clone <your-repository-url> .
```

### Step 5: Create Production Environment File

```bash
cd /opt/skyhigh
nano .env
```

Add the following (replace with your values):

```bash
# Database Configuration
POSTGRES_DB=skyhigh
POSTGRES_USER=skyhigh
POSTGRES_PASSWORD=<STRONG_PASSWORD>

# Backend Configuration
SPRING_PROFILE=prod
JWT_SECRET=<GENERATE_STRONG_SECRET>
SERVER_PORT=8080
CORS_ORIGINS=https://<CLOUDFRONT_URL>

# CloudWatch Configuration
CLOUDWATCH_ENABLED=true
```

**Generate strong secrets:**
```bash
# For JWT_SECRET (256-bit)
openssl rand -base64 32

# For POSTGRES_PASSWORD
openssl rand -base64 16
```

### Step 6: Deploy Application

```bash
cd /opt/skyhigh
./deployment/scripts/deploy.sh
```

This will:
- Pull Docker images
- Start PostgreSQL container
- Start backend container
- Run database migrations
- Perform health checks

### Step 7: Verify Deployment

**Check services status:**
```bash
docker-compose ps
```

**Check backend health:**
```bash
curl http://localhost:8080/actuator/health
```

**View logs:**
```bash
docker-compose logs -f backend
```

### Step 8: Deploy Frontend to S3

**Option A: Using GitHub Actions (Recommended)**

Push your code to the `main` branch, and GitHub Actions will automatically:
- Build the frontend
- Deploy to S3
- Invalidate CloudFront cache

**Option B: Manual Deployment**

```bash
# Build frontend locally
cd frontend
npm install
npm run build

# Deploy to S3
aws s3 sync dist/ s3://<S3_BUCKET_NAME>/ --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation \
  --distribution-id <CLOUDFRONT_DISTRIBUTION_ID> \
  --paths "/*"
```

### Step 9: Access Application

- **Frontend**: https://<CLOUDFRONT_URL>
- **Backend API**: http://<EC2_PUBLIC_IP>/api/v1
- **Health Check**: http://<EC2_PUBLIC_IP>/actuator/health

## Post-Deployment Configuration

### Configure CloudWatch Alarms

1. **CPU Utilization Alarm**
   ```bash
   aws cloudwatch put-metric-alarm \
     --alarm-name skyhigh-high-cpu \
     --alarm-description "Alert when CPU exceeds 80%" \
     --metric-name CPUUtilization \
     --namespace AWS/EC2 \
     --statistic Average \
     --period 300 \
     --threshold 80 \
     --comparison-operator GreaterThanThreshold \
     --evaluation-periods 2
   ```

2. **Memory Utilization Alarm**
   ```bash
   aws cloudwatch put-metric-alarm \
     --alarm-name skyhigh-high-memory \
     --alarm-description "Alert when memory exceeds 80%" \
     --metric-name MemoryUtilization \
     --namespace CWAgent \
     --statistic Average \
     --period 300 \
     --threshold 80 \
     --comparison-operator GreaterThanThreshold \
     --evaluation-periods 2
   ```

### Set Up Automated Backups

**Database Backup Cron Job:**

```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * /opt/skyhigh/deployment/scripts/backup-db.sh
```

**Create backup script:**

```bash
cat > /opt/skyhigh/deployment/scripts/backup-db.sh << 'EOF'
#!/bin/bash
BACKUP_DIR="/opt/skyhigh/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

docker exec skyhigh-postgres pg_dump -U skyhigh skyhigh > \
  $BACKUP_DIR/db_backup_$TIMESTAMP.sql

# Upload to S3
aws s3 cp $BACKUP_DIR/db_backup_$TIMESTAMP.sql \
  s3://<S3_BUCKET_NAME>/backups/

# Keep only last 30 days locally
find $BACKUP_DIR -name "db_backup_*.sql" -mtime +30 -delete
EOF

chmod +x /opt/skyhigh/deployment/scripts/backup-db.sh
```

### Configure SSL/TLS (Optional)

**For custom domain:**

1. **Request ACM Certificate**
   ```bash
   aws acm request-certificate \
     --domain-name yourdomain.com \
     --validation-method DNS
   ```

2. **Update CloudFront Distribution**
   - Add custom domain
   - Attach ACM certificate
   - Update DNS records

3. **Update CORS Configuration**
   ```bash
   # In .env file
   CORS_ORIGINS=https://yourdomain.com
   ```

## Maintenance Operations

### Update Application

```bash
cd /opt/skyhigh
./deployment/scripts/deploy.sh
```

### Rollback to Previous Version

```bash
cd /opt/skyhigh
./deployment/scripts/rollback.sh
```

### View Logs

```bash
# All logs
docker-compose logs -f

# Backend only
docker-compose logs -f backend

# PostgreSQL only
docker-compose logs -f postgres
```

### Restart Services

```bash
# Restart all
docker-compose restart

# Restart backend only
docker-compose restart backend
```

### Database Operations

**Connect to database:**
```bash
docker exec -it skyhigh-postgres psql -U skyhigh -d skyhigh
```

**Manual backup:**
```bash
docker exec skyhigh-postgres pg_dump -U skyhigh skyhigh > backup.sql
```

**Restore from backup:**
```bash
docker exec -i skyhigh-postgres psql -U skyhigh -d skyhigh < backup.sql
```

## Monitoring

### CloudWatch Metrics

- CPU Utilization
- Memory Utilization
- Disk I/O
- Network Traffic
- Application logs

**View metrics:**
```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=<INSTANCE_ID> \
  --start-time 2026-02-27T00:00:00Z \
  --end-time 2026-02-27T23:59:59Z \
  --period 3600 \
  --statistics Average
```

### Application Metrics

**Backend metrics endpoint:**
```bash
curl http://localhost:8080/actuator/metrics
```

**Specific metric:**
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

## Troubleshooting

### EC2 Instance Issues

**Can't SSH into instance:**
- Verify security group allows your IP
- Check Elastic IP is attached
- Verify key pair permissions: `chmod 400 your-key.pem`

**Services not starting:**
```bash
# Check Docker status
sudo systemctl status docker

# Check disk space
df -h

# Check memory
free -m
```

### Application Issues

**Backend not responding:**
```bash
# Check container status
docker-compose ps

# View logs
docker-compose logs backend

# Restart backend
docker-compose restart backend
```

**Database connection errors:**
```bash
# Check PostgreSQL status
docker-compose ps postgres

# View PostgreSQL logs
docker-compose logs postgres

# Verify credentials in .env
```

### Frontend Issues

**CloudFront not serving latest version:**
```bash
# Invalidate cache
aws cloudfront create-invalidation \
  --distribution-id <DISTRIBUTION_ID> \
  --paths "/*"
```

**S3 sync issues:**
```bash
# Verify bucket exists
aws s3 ls s3://<BUCKET_NAME>

# Check IAM permissions
aws iam get-user
```

## Cost Optimization

### Monthly Cost Breakdown (MVP)

- EC2 t3.medium: ~$30
- EBS 30GB: ~$3
- S3 + CloudFront: ~$5-10
- CloudWatch: ~$5
- **Total: ~$43-48/month**

### Cost Saving Tips

1. **Stop EC2 during non-business hours** (if applicable)
   ```bash
   aws ec2 stop-instances --instance-ids <INSTANCE_ID>
   ```

2. **Use S3 lifecycle policies**
   - Move old backups to Glacier
   - Delete old logs after 90 days

3. **Optimize CloudFront caching**
   - Increase cache TTL for static assets
   - Use compression

4. **Monitor and optimize database queries**
   - Use indexes effectively
   - Optimize slow queries

## Security Best Practices

1. **Rotate credentials regularly**
   - JWT secret
   - Database password
   - AWS access keys

2. **Keep software updated**
   ```bash
   sudo yum update -y
   docker-compose pull
   ```

3. **Monitor access logs**
   ```bash
   docker-compose logs | grep "401\|403\|500"
   ```

4. **Enable MFA for AWS account**

5. **Use AWS Secrets Manager** (for production)
   - Store sensitive credentials
   - Automatic rotation

## Disaster Recovery

### Backup Strategy

- **Database**: Daily automated backups to S3
- **Configuration**: Version controlled in Git
- **Docker images**: Stored in Docker Hub

### Recovery Procedure

1. **Deploy new infrastructure** (if needed)
   ```bash
   cd deployment/aws
   ./deploy-infrastructure.sh
   ```

2. **Restore application**
   ```bash
   cd /opt/skyhigh
   git clone <repository-url> .
   cp .env.backup .env
   docker-compose up -d
   ```

3. **Restore database**
   ```bash
   aws s3 cp s3://<BUCKET>/backups/latest.sql backup.sql
   docker exec -i skyhigh-postgres psql -U skyhigh -d skyhigh < backup.sql
   ```

## Support

For issues or questions:
- Check logs: `docker-compose logs -f`
- Review CloudWatch metrics
- Contact SkyHigh Engineering Team

---

**Last Updated**: February 27, 2026
