# Task 001: Project Setup and Infrastructure

## Objective
Set up the foundational project structure, development environment, and AWS infrastructure for the SkyHigh Core MVP.

## Scope
- Initialize backend (Spring Boot) and frontend (React) projects
- Configure AWS infrastructure (EC2, S3, CloudFront)
- Set up Docker and Docker Compose
- Configure CI/CD pipeline with GitHub Actions
- Set up monitoring with CloudWatch

## Key Deliverables

### 1. Project Structure
- [ ] Create backend folder with Spring Boot Maven project
- [ ] Create frontend folder with React + TypeScript + Vite
- [ ] Create deployment folder for Docker and infrastructure configs
- [ ] Initialize Git repository with proper .gitignore files

### 2. AWS Infrastructure
- [ ] Create VPC (10.0.0.0/16) with public subnet
- [ ] Provision EC2 instance (t3.medium) with Amazon Linux 2023
- [ ] Attach Elastic IP to EC2 instance
- [ ] Configure Security Group (ports 80, 443, 22)
- [ ] Create S3 bucket for frontend hosting
- [ ] Set up CloudFront distribution with SSL certificate
- [ ] Configure CloudWatch for logs and metrics

### 3. Docker Setup
- [ ] Install Docker Engine and Docker Compose on EC2
- [ ] Create Dockerfile for backend
- [ ] Create Dockerfile for frontend
- [ ] Create docker-compose.yml with PostgreSQL and backend services
- [ ] Configure persistent volumes for PostgreSQL data

### 4. CI/CD Pipeline
- [ ] Set up GitHub Actions workflow for backend
- [ ] Set up GitHub Actions workflow for frontend
- [ ] Configure Docker Hub integration
- [ ] Create deployment scripts (deploy.sh)
- [ ] Configure automated testing in pipeline

### 5. Environment Configuration
- [ ] Create .env files for backend (dev, prod)
- [ ] Create .env files for frontend (dev, prod)
- [ ] Configure environment variables for JWT secret, DB password
- [ ] Set up secrets management in GitHub Actions

## Dependencies
- AWS account with appropriate permissions
- GitHub repository
- Docker Hub account

## Success Criteria
- EC2 instance is running and accessible
- Docker Compose successfully starts PostgreSQL container
- Frontend is accessible via CloudFront URL
- CI/CD pipeline successfully builds and deploys
- CloudWatch is collecting logs and metrics

## Estimated Effort
High-level setup task

## References
- TRD.md Section 4: Infrastructure Design
- TRD.md Section 10: Deployment Strategy
- TRD.md Section 11: Monitoring & Logging
