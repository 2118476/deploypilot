# DeployPilot

**Build it. Secure it. Deploy it. Know what to do next.**

DeployPilot is your interactive deployment assistant. It helps developers confidently deploy their applications by providing personalized deployment plans, step-by-step guides, a Git command reference, environment variable management, AI-powered troubleshooting — and now read-only GitHub repository analysis.

## Features

- **Repository Analysis (new)** - Point DeployPilot at a GitHub repository and it detects the technology stack from configuration files — read-only, with evidence and confidence for every finding
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

## Repository Analysis

### What it does

Open a project, enter a GitHub repository (`owner/name` or URL), and DeployPilot will:

- List the repository's files through the GitHub REST API (read-only)
- Download only recognized **configuration files** (`package.json`, `pom.xml`, `Dockerfile`, `netlify.toml`, `render.yaml`, `.env.example`, and similar)
- Detect, deterministically and without AI: project structure (monorepo vs single app), frontend/backend frameworks, languages, build tools, package managers, databases, container usage, hosting configuration, likely external services, and build/start commands
- Extract **environment-variable names** from `.env.example`-style templates and classify them (secret/sensitive, public, configuration) — names, classification and source file only
- Attach **evidence** (which file proved what) and a **confidence level** to every detection, plus warnings for anything skipped or unparseable

### What it does not do yet

- It does **not** modify your repository in any way (no writes, commits, branches or pull requests)
- It does **not** deploy anything or call the Netlify/Render APIs
- It does **not** read real `.env` files or return secret values
- It does **not** send your repository content to AI

### GitHub authentication setup

| Mode | Setup | Capability |
|---|---|---|
| No token (default) | none | Public repositories only; GitHub's 60 requests/hour unauthenticated limit |
| Fine-grained PAT | Set `GITHUB_API_TOKEN` on the backend | Private repositories the token can read; 5,000 requests/hour |
| Fixture mode | Set `REPO_ACCESS_MODE=fixture` | Serves the bundled sample repository `demo/sample-monorepo` — for development and tests, no network |

To analyse private repositories, create a **fine-grained personal access token** (GitHub → Settings → Developer settings → Fine-grained tokens) with:

- Repository access: only the repositories you want to analyse
- Permissions: **Contents: Read-only** and **Metadata: Read-only** — nothing else

Set it as `GITHUB_API_TOKEN` on the backend (e.g. in Render's environment settings). The token is read from the environment only — it is never stored in the database and never sent to the frontend.

**Roadmap: GitHub App.** The provider abstraction (`RepositoryFileReader` / `GitHubRepositoryClient`) is designed so a GitHub App installation (per-user OAuth, short-lived installation tokens) can replace the server-wide token. That requires registering a GitHub App (name, homepage, callback URL, private key) on a real GitHub account, which is external configuration outside this codebase.

### Safety limits

Analysis enforces hard limits: max 20,000 files listed, max 30 configuration files downloaded, 200 KB per file, 2 MB total, 60-second budget per run, and request timeouts on every GitHub call. Dependency folders (`node_modules`, `target`, `vendor`, `dist`, …), binaries and real `.env` files are never downloaded. Analysis records are private to the owning user and project.

## Architecture

```
React Frontend (Netlify)
  |
  |-- JWT Auth --> Spring Boot Backend (Render)
                        |
                        |-- Flyway Migrations
                        |-- Supabase PostgreSQL
                        |-- Gemini API (AI troubleshooting only)
                        |-- GitHub REST API (read-only repository analysis)
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
./mvnw spring-boot:run   # or: mvn spring-boot:run
```
The backend will be available at `http://localhost:8080/api`

4. Start the frontend:
```bash
cd frontend
npm install
npm run dev
```
The frontend will be available at `http://localhost:5173`

Tip: to try repository analysis without any GitHub setup, start the backend with `REPO_ACCESS_MODE=fixture` and analyse the repository `demo/sample-monorepo`.

### Environment Variables

**Frontend** (`.env.local` in `frontend/`, or Netlify environment):

| Name | Required | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | yes | Backend base URL including `/api`, e.g. `https://your-backend.onrender.com/api` |
| `VITE_APP_NAME` | no | Display name |

**Backend** (environment variables; see `.env.example`):

| Name | Required | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | `dev` locally, `prod` in production |
| `DATABASE_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://host:5432/db` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | yes | Database credentials |
| `JWT_SECRET` | yes in prod | Signing secret, minimum 32 characters. **Production refuses to start if it is missing, too short, or left at the dev default.** |
| `FRONTEND_URL` | yes in prod | Allowed CORS origin(s), comma-separated |
| `GEMINI_API_KEY` | no | Enables AI troubleshooting |
| `GITHUB_API_TOKEN` | no | Fine-grained read-only token for private-repository analysis and higher rate limits |
| `REPO_ACCESS_MODE` | no | `github` (default) or `fixture` for the bundled sample repository |

## Testing

Backend (unit + integration tests; uses in-memory H2 and the fixture repository — no network or secrets needed):
```bash
cd backend
mvn test
```

Frontend build and lint:
```bash
cd frontend
npm ci
npm run lint
npm run build
```

CI runs all of the above on every push and pull request to `main` (`.github/workflows/ci.yml`). CI requires no production secrets.

## Deployment

### 1. Create Supabase Database
- Create a project at [supabase.com](https://supabase.com)
- Use the **Session pooler** connection (IPv4) for Render; convert to JDBC form `jdbc:postgresql://host:5432/postgres`

### 2. Deploy Backend to Render
- Create a Web Service at [render.com](https://render.com) (Docker, `backend/Dockerfile`)
- Set the environment variables listed above

### 3. Deploy Frontend to Netlify
- Create a site at [netlify.com](https://netlify.com)
- `netlify.toml` configures the build (base `frontend`, publish `dist`)
- Add `VITE_API_BASE_URL` pointing to your Render backend (including `/api`)

### 4. Configure CORS
- Set `FRONTEND_URL` on Render to your Netlify URL
- The backend allows requests only from the configured origin(s)

## API Documentation

Once the backend is running, API documentation is available at:
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

## Security

- JWT authentication with BCrypt password hashing
- **Fail-fast production secrets**: with the `prod` profile, startup aborts when `JWT_SECRET` is absent, shorter than 32 characters, or still the development default
- Automatic secret redaction before error text is sent to AI
- Repository analysis returns environment-variable **names only** — never values; real `.env` files are never read
- No GitHub tokens in the database — credentials come from the environment only
- Per-user ownership enforced on projects, deployment plans, env-var records and analysis results
- CORS restricted to configured origins
- Input validation and size limits on all write endpoints
- No stack traces exposed in production

### Known limitation: JWT in localStorage

The frontend stores the JWT in `localStorage`. This is simple and works across tabs, but any successful XSS attack could read the token. Mitigations in place: React's default output escaping, no `dangerouslySetInnerHTML`, short token lifetime (24 h). A move to `HttpOnly` cookies with CSRF protection is a candidate for a later phase; it was deliberately not rushed into this one to avoid a broad auth rewrite.

## Live Deployment Verification (Stage 3)

After you deploy by following the blueprint, open a project → **Verify Deployment**, enter your live frontend and
backend URLs, and DeployPilot runs read-only checks and explains what works, what's broken, and what to fix next.

It checks (deterministically, no AI needed): frontend serves HTML and its assets load; SPA routes survive a direct
refresh; no Netlify/Vercel/Render provider error page; the bundle isn't pointing at localhost or an unreplaced
`${...}` placeholder; the backend responds and its health endpoint works; the `/api` prefix isn't missing or
duplicated; no stack traces are exposed; a safe CORS preflight confirms the backend accepts the **production**
frontend origin (accepted / rejected / wrong-origin / wildcard-credentials-conflict / unknown); the deployed
version vs an expected commit (CURRENT / OUTDATED / MISMATCHED / UNKNOWN, with a suggested `/version.json` format
when nothing is exposed); and — for PWAs — manifest, service-worker content-type/scope, `sw.js`-rewritten-to-HTML,
and cache headers for HTML / worker / hashed assets. Root-cause diagnoses carry severity, confidence
(CONFIRMED / LIKELY / POSSIBLE / USER-DEVICE), evidence and an exact next action.

An **important UNKNOWN never becomes HEALTHY**, and a frontend that can't talk to its backend is never HEALTHY.

**Project-aware troubleshooter**: summarises this project's analysis, blueprint and latest verification into a minimal,
secret-redacted payload and (if `GEMINI_API_KEY` is set) asks AI to explain it as verified facts / likely
explanations / next steps. Works without AI too. Pasted logs are size-limited and redacted before they leave your
session; automated redaction reduces but can't guarantee removal of every secret.

**Security**: every URL is treated as untrusted. Only HTTPS (HTTP allowed only in explicit local-dev mode), only
GET/HEAD/OPTIONS, no credentials in URLs, and localhost / loopback / private / link-local / cloud-metadata addresses
are blocked — including on redirect hops. Connect/read/total timeouts, response-size caps, no binary downloads, and
DeployPilot never forwards its own auth headers. Verification only runs against URLs on your own projects.

## Controlled Deployment Automation (Stage 4)

After you have a blueprint, open a project → **Automate deployment**. DeployPilot can connect to your own GitHub,
Netlify and Render accounts, turn the blueprint into an **exact, classified action plan**, ask for explicit confirmation
and then perform only the actions you approved — always showing what it intends to change before anything happens.

### Connections (per user, encrypted)

Each user connects their own accounts on the **Connections** page — DeployPilot no longer relies on one server-wide
GitHub token. Provider tokens are validated against the provider, encrypted at rest (AES-256-GCM) and **never returned
to the browser or written to logs**. You can disconnect at any time, and connection status and granted permissions are
shown.

| Provider | Credential | Minimum permissions |
|---|---|---|
| GitHub | Fine-grained personal access token (or a GitHub App installation token) | Contents: Read and write · Pull requests: Read and write · Metadata: Read |
| Netlify | Personal access token (or OAuth token) | Manage sites, builds and environment variables |
| Render | API key | Manage web services, environment variables and deploys |

### Three modes

1. **Guide Me** — explains every step; you perform provider actions manually.
2. **Prepare for Me** — generates configuration, variable mappings and the action plan; changes nothing externally.
3. **Deploy for Me** — performs only the actions you explicitly confirm.

The default performs no external changes automatically.

### The action plan

Generated deterministically from the latest analysis, blueprint and verification. Before anything runs it shows the
repository and branch, the components being deployed, the provider and account, which resources are **created** vs
**changed**, build/start/publish settings, environment-variable **names and destinations** (secret values masked),
the deployment order, possible costs, actions that require a repository change, and which actions are reversible.
Every action is classified `READ_ONLY`, `CREATE`, `UPDATE`, `DEPLOY`, `RESTART` or `DESTRUCTIVE` (no destructive
actions are generated in this stage).

### Confirmation and safety

Execution requires a **short-lived, single-use confirmation** bound to the user, project, provider accounts, the exact
plan hash and the repository/commit. It cannot be replayed, expires quickly, and if the plan changes after you confirm,
you must confirm again. Execution is **idempotent** (retry from a failed step without recreating resources), never
creates duplicate services, **never auto-selects a paid plan**, never deletes provider resources, never overwrites an
unrelated service, and **never commits to or force-pushes `main`** — configuration files go through a pull request.
Secret values never appear in logs, errors or the ownership-protected audit record.

### What it automates (after confirmation)

- **GitHub** — read the repo, check the latest commit, prepare deployment files, show diffs, create a dedicated branch,
  commit generated config (`netlify.toml`, `render.yaml`, `Dockerfile`, …) and open a pull request. Never commits to
  `main`; opens no PR when nothing changed.
- **Render** — reuse or create a Web Service on the free plan, connect the repo, set branch/root/runtime and build/start
  commands and health-check path, set backend variables, deploy, monitor, read sanitised logs and capture the backend URL.
- **Netlify** — reuse or create a site on the free plan, connect the repo, set branch/base/build/publish, set only
  frontend-safe variables, deploy, monitor, read sanitised logs and capture the production URL.
- **Dependency-aware execution** — confirm the database, configure and deploy the backend, capture its URL, set the
  frontend API URL, deploy the frontend, capture its URL, set the backend CORS/frontend-origin variable, restart the
  backend, then **run Stage 3 verification automatically**. It stops on an important failure and never claims success
  unless verification supports it.
- **Database handoff** — uses the blueprint to decide whether a database is required. In this stage DeployPilot imports
  existing connection details securely (encrypted), shows exact instructions for Supabase / Render PostgreSQL, tests
  only safe connectivity, and **pauses to ask for the connection fields** rather than pretending a database was created.

### Secrets

Application secrets such as JWT keys are generated with a cryptographically secure generator. DeployPilot never
fabricates provider or database credentials — you supply provider-issued keys, which are stored encrypted only while
needed, masked in the interface, never returned after saving, and replaceable/removable.

### External setup required from the owner

The provider **token/API-key** path above works with no external app registration — each user creates a token in their
own provider settings. Two optional upgrades require registering an application on a real account (external
configuration, outside this codebase):

- **GitHub App** (instead of a fine-grained PAT): register an App and set its callback URL to
  `https://<your-frontend>/connections`, request the minimum permissions (Contents: read/write, Pull requests:
  read/write, Metadata: read), and provide the App's client id/secret and private key as backend environment variables.
- **Netlify OAuth** (instead of a PAT): register an OAuth application with the callback URL
  `https://<your-frontend>/connections` and provide its client id/secret to the backend.

The one new backend environment variable is `DEPLOYPILOT_ENCRYPTION_KEY` (see `.env.example`). It is required to **use**
connections and automation: without a strong value the app still runs, but connecting providers and storing secrets are
disabled (fail-closed) so nothing is ever stored under a weak key. `render.yaml` provisions it automatically
(`generateValue`) for blueprint-managed Render services; otherwise set it in the service's environment
(e.g. `openssl rand -base64 32`).

## Roadmap

1. ~~Read-only repository analysis~~ ✓
2. ~~Deployment blueprint~~ ✓
3. ~~Live deployment verification~~ ✓ — checks DNS, health, CORS, version and PWA/cache against the blueprint
4. ~~Controlled deployment automation~~ ✓ — per-user encrypted connections, a classified action plan, short-lived
   confirmation, and idempotent GitHub/Netlify/Render deployment with automatic verification (never without consent)

## License

MIT
