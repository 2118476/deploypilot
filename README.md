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

## Project Copilot, Intelligent Dashboard & Controlled Supabase Automation (Stage 5)

Stage 5 adds a persistent, project-aware **Copilot**, a deterministic **intelligent dashboard**, and **controlled
Supabase database automation** — all reusing the Stage 4 ownership, encryption, plan-hash, confirmation, sanitization
and verification systems.

### Project Copilot
- Persistent, per-project conversations (bound to `userId` + `projectId`, ownership enforced on every read/create/clear).
- Answers from **real records** first (deterministic), with Gemini adding a plain-language explanation when available.
- `ProjectContextService` assembles a **bounded, sanitized** snapshot (analysis, blueprint, connections, latest run and
  step, sanitized logs, outputs, verification, missing secret names, relevant filenames). `SecretRedactionUtil` +
  `LogSanitizer` run before the AI request and again before persistence. It never includes credentials, secret values,
  JWTs, database passwords, real `.env` contents or unredacted logs.
- Never claims an action succeeded without an `AutomationRun` / `ExecutionStep` / provider result / `VerificationRun` to
  prove it. Bounded history with a clear-conversation action (clearing never touches automation/verification history).
- **Safe action requests:** "Deploy this project" or "Retry the failed step" produce a typed *proposed action* and a
  deterministic plan via `ActionPlanService`, then send you to the existing review-and-confirm flow. The AI never
  generates or approves a confirmation nonce, never executes anything, and its free-form output is never trusted as JSON.

### Intelligent dashboard
- `ProjectStatusService` computes a deterministic status (`NOT_ANALYSED` … `HEALTHY`/`DEGRADED`/`FAILED`) from stored
  records only — it works with Gemini unavailable. The dashboard shows the status, "what DeployPilot is doing now", a
  completed-milestone timeline, "what you need to do" cards, one recommended next action, deployment URLs, verification
  result, recent activity, and the compact Copilot panel. No fake percentage progress.

### Controlled Supabase automation
- Supabase is a **`DatabaseProvider`** (not a `HostingProvider`). Connect a Supabase **personal access token**; it is
  validated against the Management API, encrypted at rest and never returned or logged.
- With a database component, a Supabase connection, an explicit choice (existing/new project) and Deploy-for-me mode,
  the plan gains `database.inspect|create|wait|migrations.inspect|migrations.apply|credentials`, `backend.database-env`
  and `verify.database` steps — each classified and shown before confirmation, with the database choices bound into the
  plan hash. **Never selects a paid plan**; if free creation is unavailable it **pauses and explains** rather than
  pretending success. Idempotent (reuses a created project on retry; migration checksums prevent re-applying).
- **Migration safety:** only repository-owned migrations (`supabase/migrations`, `database/migrations`, `db/migration`,
  …) are considered — never AI-generated SQL. Filenames, order, checksums, applied-state and safety classification are
  shown; potentially destructive statements (`DROP …`, `TRUNCATE`, destructive `ALTER`, unrestricted `DELETE`) are
  **blocked** for manual expert review. Applied checksums are recorded in `applied_database_migrations`.
- **Variable routing:** `DATABASE_URL`, `JDBC_DATABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` and the DB password stay
  backend-only; only public values (`VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `NEXT_PUBLIC_SUPABASE_*`) may reach
  the frontend host. Passwords and service-role keys never enter run outputs, logs, AI context or audit events.

### External Supabase setup required
- Each user creates a **Supabase personal access token** (Supabase → Account → Access Tokens) and connects it on the
  **Connections** page. Minimum use: read organizations/projects, create a free project, read API keys, run approved
  migrations. No server-side Supabase environment variable is required. Supabase OAuth is intentionally left as a future
  upgrade. `GEMINI_API_KEY` is optional — without it the Copilot and dashboard use deterministic answers.

## Evidence-Driven Copilot Troubleshooter (Stage 6)

Stage 6 upgrades the Copilot into a smart, **evidence-first** deployment troubleshooter. It explains a failure to a
beginner: what failed, what is *proven* by DeployPilot's records, what is only a *likely* explanation, what was already
tried, the exact next steps, what evidence is still needed, what success looks like, and whether **Retry** is safe now.

### Architecture: deterministic evidence first, Gemini second
- **`TroubleshootingContextService`** builds a bounded, already-sanitised **`TroubleshootingContext`** for one failed
  step: repository identity/visibility/branch, latest run + status, the exact failed step (id/title/provider/time),
  completed and pending steps, the failed-step log (capped ~5,000 chars), failure reason, safe outputs (frontend/backend
  /PR URLs), verification status + failing/warning checks, connection status, safe provider metadata, previous
  recommendations, remedies already attempted, the known manual-deploy result, and the missing evidence. It reuses
  `ProjectContextService` and runs `LogSanitizer` + `SecretRedactionUtil` — it **never** carries tokens, passwords,
  service-role keys, DB credentials, Authorization headers, cookies, private env-var values or raw provider bodies.
- **`FailureClassifier`** is deterministic **ground truth**. It maps a failure to one `TroubleshootingErrorCode`
  (Netlify host-key clone failure, 401 auth, 403 permission/plan, build failure, publish dir, missing frontend env,
  Render Dockerfile missing, Render build, cold start, backend health, CORS origin, duplicated `/api`, Supabase
  connection, missing secret, inconclusive verification, version mismatch, or `UNKNOWN`) and returns a structured
  diagnosis with verified facts, likely causes, safe numbered steps, required evidence, a retry-safety verdict and a
  status (`DIAGNOSED` / `NEEDS_EVIDENCE` / `READY_TO_RETRY` / `UNKNOWN`).
- **`TroubleshootingService`** is the single, unified brain. It classifies, adds read-only provider diagnostics, then —
  only if `GEMINI_API_KEY` is set — asks Gemini for a **validated structured JSON** explanation that ranks the evidence.
  Gemini may reword the summary and re-rank causes but **can never change the error code, status or retry-safety verdict**.
  A missing key, malformed JSON or a timeout falls back to the deterministic diagnosis; raw malformed AI JSON is never
  shown. The single `GeminiAiProvider` (header `x-goog-api-key`, timeouts, size caps, sanitised failures) is the only
  Gemini client — no second key, never exposed to Vite/frontend code.

### Host-key failure (the JobPilot case) — no repetitive loops
For `Host key verification failed` / `Could not read from remote repository` / `exit status 128`, the Copilot says
Netlify could not download the repository (**not** an application-code bug), and never auto-unlinks a valid GitHub App
connection (preserving the fix from PR #25). It asks whether **Netlify's own** deploy succeeded after relinking and
branches:
- **Case A — Netlify's own deploy also fails** → GitHub authorisation is still broken; re-authorise the GitHub App,
  retry is not safe.
- **Case B — Netlify's own deploy succeeds but DeployPilot's failed** → authorisation is fixed; retry from the failed
  step (and if it still fails, compare DeployPilot's trigger/selected resource).

Safe events (`RELINK_REPOSITORY_RECOMMENDED`, `USER_REPORTED_RELINK_COMPLETED`, `MANUAL_DEPLOY_SUCCEEDED/FAILED/UNKNOWN`,
`RETRY_RECOMMENDED/ATTEMPTED`, `SAME_FAILURE_REPEATED`) are recorded (no screenshots, no secrets). Once the user reports
they relinked, the Copilot **stops repeating "relink"** and asks for Netlify's own deploy result instead; if the same
failure repeats after a remedy, it escalates to gathering new evidence rather than giving identical instructions.

### On the deployment screen
`AutomateDeploymentPage` shows an **"Ask Copilot about this failure"** control beside a failed step (and in the failure
banner). It auto-uses the project, run, failed step, provider, sanitised log, previous remedies and verification result —
no copy/paste. The `TroubleshootingPanel` renders what failed, verified facts, likely cause, exact steps, information
needed (with "what not to share" warnings), and the retry recommendation, plus quick questions ("Why did this fail?",
"Is it safe to retry?", "Did the deployment actually succeed?", …) and buttons to report the manual-deploy result. The
dashboard Copilot remains; both surfaces share the same backend brain and the single Gemini client.

### What the Copilot can and cannot do
- **Can:** read DeployPilot's own records and safe read-only provider metadata, classify failures deterministically,
  explain and rank evidence, recommend exact next steps, and say whether Retry is safe.
- **Cannot:** execute deployments, confirmations, provider/repository/database mutations, or generate a confirmation
  nonce. All changes still go through DeployPilot's reviewed plan + confirmation flow. A failed diagnostics read becomes
  `UNKNOWN`, never a guess; Gemini never claims to have inspected a dashboard it did not read through an authorised
  read-only adapter, and never asks the user to paste tokens, passwords or environment-variable values.

## Roadmap

1. ~~Read-only repository analysis~~ ✓
2. ~~Deployment blueprint~~ ✓
3. ~~Live deployment verification~~ ✓ — checks DNS, health, CORS, version and PWA/cache against the blueprint
4. ~~Controlled deployment automation~~ ✓ — per-user encrypted connections, a classified action plan, short-lived
   confirmation, and idempotent GitHub/Netlify/Render deployment with automatic verification (never without consent)
5. ~~Project Copilot, intelligent dashboard & controlled Supabase automation~~ ✓ — a persistent evidence-based Copilot,
   a deterministic status dashboard, and confirmed, idempotent Supabase database preparation with migration safety
6. ~~Evidence-driven Copilot troubleshooter~~ ✓ — a deterministic failure classifier as ground truth, a validated
   structured Gemini explanation that can never contradict it, host-key Case A/B branching, loop prevention, read-only
   provider diagnostics, and an "Ask Copilot about this failure" control on the deployment screen

## License

MIT
