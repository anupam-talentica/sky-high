# SkyHigh Core - Digital Check-In System

A modern, scalable digital check-in system for airlines built with Spring Boot and React.

## 📋 Overview

SkyHigh Core is a comprehensive digital check-in solution that enables passengers to:
- Check in online for their flights
- Select and reserve seats
- Add baggage information
- Join waitlists for preferred seats
- Receive real-time updates on seat availability

## 🏗️ Architecture

- **Backend**: Java 17, Spring Boot 3.2.2, PostgreSQL 15
- **Frontend**: React 18, TypeScript, Vite
- **Caching**: Caffeine (in-memory)
- **Database**: PostgreSQL with Flyway migrations
- **Deployment**: Docker, Docker Compose, AWS (EC2, S3, CloudFront)
- **CI/CD**: GitHub Actions

## 📁 Project Structure

```
Sky-High/
├── backend/                 # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/skyhigh/
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── repository/
│   │   │   │   ├── entity/
│   │   │   │   ├── dto/
│   │   │   │   ├── config/
│   │   │   │   ├── exception/
│   │   │   │   └── util/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                # React application
│   ├── src/
│   │   ├── components/
│   │   ├── services/
│   │   ├── hooks/
│   │   ├── utils/
│   │   ├── types/
│   │   ├── contexts/
│   │   ├── pages/
│   │   └── assets/
│   ├── package.json
│   ├── Dockerfile
│   └── nginx.conf
├── deployment/              # Deployment configurations
│   ├── aws/
│   │   ├── cloudformation-template.yml
│   │   └── deploy-infrastructure.sh
│   ├── scripts/
│   │   ├── setup-ec2.sh
│   │   ├── deploy.sh
│   │   └── rollback.sh
├── .github/workflows/       # CI/CD pipelines
├── docker-compose.yml       # Local development setup
├── PRD.md                   # Product Requirements Document
├── TRD.md                   # Technical Requirements Document
└── README.md
```

## 🚀 Getting Started

### Prerequisites

- **Java 17+** (for backend development)
- **Node.js 20+** (for frontend development)
- **Docker & Docker Compose** (for containerized deployment)
- **PostgreSQL 15** (if running locally without Docker)
- **Maven 3.9+** (for backend builds)

### Local Development Setup

#### Option 1: Using Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Sky-High
   ```

2. **Create environment file**
   ```bash
   cp .env.example .env
   # Edit .env with your local configuration
   ```

3. **Start all services**
   ```bash
   docker-compose up -d
   ```

4. **Access the application**
   - Backend API: http://localhost:8080
   - Frontend: http://localhost:80
   - API Health Check: http://localhost:8080/actuator/health

5. **View logs**
   ```bash
   docker-compose logs -f
   ```

6. **Stop services**
   ```bash
   docker-compose down
   ```

#### Option 2: Running Services Individually

**Backend:**

1. **Set up PostgreSQL database**
   ```bash
   # Create database
   createdb skyhigh
   
   # Or using Docker
   docker run -d \
     --name postgres \
     -e POSTGRES_DB=skyhigh \
     -e POSTGRES_USER=skyhigh \
     -e POSTGRES_PASSWORD=skyhigh123 \
     -p 5432:5432 \
     postgres:15-alpine
   ```

2. **Run backend**
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

**Frontend:**

1. **Install dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Start development server**
   ```bash
   npm run dev
   ```

3. **Access frontend**
   - Development server: http://localhost:5173

## 🧪 Testing

### Backend Tests

```bash
cd backend

# Run all tests
./mvnw test

# Run tests with coverage
./mvnw clean test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Frontend Tests

```bash
cd frontend

# Run tests
npm test

# Run tests with coverage
npm test -- --coverage

# View coverage report
open coverage/lcov-report/index.html
```

## 🏗️ Building for Production

### Backend

```bash
cd backend
./mvnw clean package -DskipTests
```

The JAR file will be created in `target/skyhigh-core-1.0.0-SNAPSHOT.jar`

### Frontend

```bash
cd frontend
npm run build
```

The production build will be created in `dist/`

## 🚢 Deployment

### AWS Infrastructure Setup

1. **Deploy infrastructure using CloudFormation**
   ```bash
   cd deployment/aws
   ./deploy-infrastructure.sh
   ```

2. **SSH into EC2 instance**
   ```bash
   ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
   ```

3. **Run setup script**
   ```bash
   cd /opt/skyhigh
   ./deployment/scripts/setup-ec2.sh
   ```

4. **Deploy application**
   ```bash
   ./deployment/scripts/deploy.sh
   ```

### Manual Deployment

1. **Build Docker images**
   ```bash
   # Backend
   docker build -t skyhigh/backend:latest ./backend
   
   # Frontend
   docker build -t skyhigh/frontend:latest ./frontend
   ```

2. **Push to Docker Hub**
   ```bash
   docker push skyhigh/backend:latest
   docker push skyhigh/frontend:latest
   ```

3. **Deploy on EC2**
   ```bash
   ssh ec2-user@<EC2_IP>
   cd /opt/skyhigh
   docker-compose pull
   docker-compose up -d
   ```

## 🔧 Configuration

### Backend Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILE` | Active Spring profile (dev/prod) | `dev` |
| `DATABASE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/skyhigh` |
| `DATABASE_USERNAME` | Database username | `skyhigh` |
| `DATABASE_PASSWORD` | Database password | `skyhigh123` |
| `JWT_SECRET` | JWT signing secret | (must be set) |
| `SERVER_PORT` | Backend server port | `8080` |
| `CORS_ORIGINS` | Allowed CORS origins | `http://localhost:5173` |

### Frontend Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_API_BASE_URL` | Backend API base URL | `http://localhost:8080/api/v1` |
| `VITE_APP_NAME` | Application name | `SkyHigh Core` |
| `VITE_APP_VERSION` | Application version | `1.0.0` |

## 📊 Monitoring & Logging

### Health Checks

- **Backend Health**: http://localhost:8080/actuator/health
- **Backend Metrics**: http://localhost:8080/actuator/metrics
- **Frontend Health**: http://localhost:80/health

### Logs

**Docker Compose:**
```bash
# View all logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f backend
docker-compose logs -f postgres
```

**Backend Logs:**
- Location: `backend/logs/skyhigh-core.log`
- Log level: Configured in `application.yml`

**CloudWatch (Production):**
- Logs are automatically sent to CloudWatch when `CLOUDWATCH_ENABLED=true`
- Log group: `/aws/ec2/skyhigh-core`

## 🔒 Security

### Authentication

- JWT-based authentication
- Token expiration: 24 hours
- Secure password hashing with BCrypt

### HTTPS

- CloudFront provides SSL/TLS termination
- Backend can be configured with SSL certificates

### Security Headers

- X-Frame-Options: SAMEORIGIN
- X-Content-Type-Options: nosniff
- X-XSS-Protection: 1; mode=block

## 🐛 Troubleshooting

### Backend won't start

1. Check PostgreSQL is running:
   ```bash
   docker-compose ps postgres
   ```

2. Check database connection:
   ```bash
   docker-compose logs postgres
   ```

3. Verify environment variables:
   ```bash
   cat .env
   ```

### Frontend build fails

1. Clear node_modules and reinstall:
   ```bash
   cd frontend
   rm -rf node_modules package-lock.json
   npm install
   ```

2. Check Node.js version:
   ```bash
   node --version  # Should be 20+
   ```

### Database migration errors

1. Check Flyway migration status:
   ```bash
   docker-compose exec backend ./mvnw flyway:info
   ```

2. Reset database (development only):
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

## 📚 Documentation

- [Product Requirements Document (PRD)](./PRD.md)
- [Technical Requirements Document (TRD)](./TRD.md)
- [Project Status](./PROJECT_STATUS.md)
- [Task Breakdown](./tasks/)

## 🤝 Contributing

1. Create a feature branch
2. Make your changes
3. Write tests
4. Ensure all tests pass
5. Submit a pull request

## 📝 License

Copyright © 2026 SkyHigh Airlines. All rights reserved.

## 👥 Team

SkyHigh Airlines Engineering Team

## 📞 Support

For issues and questions, please contact the engineering team.

---

**Version**: 1.0.0-SNAPSHOT  
**Last Updated**: February 27, 2026
