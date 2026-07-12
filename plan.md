# DeployPilot — Build Plan

## Overview
Build a complete production-ready deployment assistant application with Spring Boot backend, React frontend, PostgreSQL database, and full deployment pipeline.

## Tech Stack
- **Frontend**: React 19 + TypeScript + Vite + Tailwind CSS v3 + shadcn/ui + TanStack Query + React Router
- **Backend**: Spring Boot 3.x + Java 17 + Spring Security + JWT + Spring Data JPA + Flyway + PostgreSQL
- **Database**: PostgreSQL (local Docker / Supabase production)
- **AI**: Gemini API (backend-only)
- **Notifications**: Firebase Cloud Messaging (optional)
- **Deployment**: Netlify (frontend) + Render (backend) + Supabase (DB)

## Stage 1: Project Structure & Backend Foundation
- Create monorepo structure
- Initialize Spring Boot backend with Maven Wrapper
- Configure Spring Security, JWT, JPA, Flyway
- Create all entity classes and repositories
- Create database migrations
- Implement authentication (register/login/JWT)
- Implement core APIs (projects, deployment plans, steps, guides)
- Seed data

## Stage 2: Frontend Foundation  
- Initialize React + Vite + Tailwind project
- Set up routing, theme (light/dark), PWA
- Build shared components (Navbar, Footer, Layout)
- Build landing page
- Implement auth pages (login/register)

## Stage 3: Frontend Features (Parallel)
- Dashboard + Project wizard + Deployment plan views
- Git Command Centre + Environment Variable Centre
- Security Centre + Error Solver (with Gemini integration)
- Guides/Documentation pages + Glossary + Settings

## Stage 4: Testing & Quality
- Backend unit/integration tests
- Frontend tests
- Build verification
- Security checks

## Stage 5: GitHub & Deployment
- Create GitHub repository
- Push code
- Configure Netlify, Render, Supabase
- Deploy and verify

## Execution Strategy
Use sub-agents for parallel work where possible. The backend is complex enough to need dedicated agents. Frontend pages can be parallelized.
