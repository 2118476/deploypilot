# DeployPilot

**Build it. Secure it. Deploy it. Know what to do next.**

DeployPilot is your interactive deployment assistant. It helps developers confidently deploy their applications by providing personalized deployment plans, step-by-step guides, a Git command reference, environment variable management, and AI-powered troubleshooting.

## Features

- **Personalized Deployment Plans** - Answer a few questions about your tech stack and receive an ordered, step-by-step deployment checklist
- **Project Wizard** - 8-step guided wizard covering frontend, backend, database, hosting, and services
- **Git Command Reference** - 33+ Git commands with explanations, warnings for destructive operations, and beginner/advanced modes
- **Environment Variable Center** - Public vs secret classification with platform-specific guidance
- **AI Error Solver** - Paste errors and get intelligent troubleshooting help (with automatic secret redaction)
- **Security Center** - Pre-deployment checklists, .gitignore generator, CORS guide, and auth security best practices
- **Guides** - Platform-specific guides for Netlify, Render, Supabase, Firebase, and more
- **Glossary** - 35+ deployment terms explained with examples
- **Dark/Light Mode** - Fully responsive UI with theme support
- **PWA Support** - Install as a web app on mobile and desktop

## Architecture

```
React Frontend (Netlify)
  |
  |-- JWT Auth --> Spring Boot Backend (Render)
                        |
                        |-- Flyway Migrations
                        |-- Supabase PostgreSQL
                        |-- Gemini API (for AI troubleshooting)
```

## Technology Stack

### Frontend
- React 19 + TypeScript
- Vite (with PWA plugin)
- Tailwind CSS v3
- React Router v7
- TanStack Query v5
- Lucide React icons

### Backend
- Spring Boot 3.3.5
- Java 17
- Spring Security + JWT
- Spring Data JPA
- Flyway migrations
- PostgreSQL
- OpenAPI/Swagger (springdoc)

### Infrastructure
- **Frontend Hosting**: Netlify
- **Backend Hosting**: Render
- **Database**: Supabase PostgreSQL
- **AI**: Google Gemini API (backend-only)

## Quick Start

### Prerequisites
- Java 17+
- Node.js 20+
- PostgreSQL (or use Docker Compose)

### Local Development

1. Clone the repository:
```bash
git clone https://github.com/YOUR_USERNAME/deploypilot.git
cd deploypilot
```

2. Start PostgreSQL (using Docker Compose):
```bash
docker-compose up -d postgres
```

3. Start the backend:
```bash
cd backend
./mvnw spring-boot:run
```
The backend will be available at `http://localhost:8080/api`

4. Start the frontend:
```bash
cd frontend
npm install
npm run dev
```
The frontend will be available at `http://localhost:5173`

### Environment Variables

Copy `.env.example` to `.env` and fill in the values:

**Frontend** (`.env.local` in `frontend/`):
```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_NAME=DeployPilot
```

**Backend** (environment variables or `.env`):
```env
SPRING_PROFILES_ACTIVE=dev
DATABASE_URL=jdbc:postgresql://localhost:5432/deploypilot
DATABASE_USERNAME=deploypilot
DATABASE_PASSWORD=localdev
JWT_SECRET=your-local-jwt-secret
```

## Deployment

### 1. Create Supabase Database
- Create a project at [supabase.com](https://supabase.com)
- Save the connection details (host, database, user, password)

### 2. Deploy Backend to Render
- Create a Web Service at [render.com](https://render.com)
- Connect your GitHub repository
- Set environment variables from `.env.example`

### 3. Deploy Frontend to Netlify
- Create a site at [netlify.com](https://netlify.com)
- Connect your GitHub repository
- Configure build settings (base: `frontend`, build: `npm ci && npm run build`, publish: `frontend/dist`)
- Add `VITE_API_BASE_URL` pointing to your Render backend

### 4. Configure CORS
- Update `FRONTEND_URL` on Render to match your Netlify URL
- The backend will automatically allow requests from that origin

## API Documentation

Once the backend is running, API documentation is available at:
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

## Security

- JWT authentication with BCrypt password hashing
- Automatic secret redaction before sending to AI
- No secrets stored in the database (only metadata)
- CORS configured per-environment
- Input validation on all endpoints
- No stack traces exposed in production

## License

MIT
