package com.deploypilot.util;

import com.deploypilot.dto.DeploymentStepDto;
import com.deploypilot.dto.TechnologySelectionRequest;
import com.deploypilot.model.enums.StepStatus;

import java.util.ArrayList;
import java.util.List;

public class DeploymentPlanGenerator {

    public static List<DeploymentStepDto> generatePlan(TechnologySelectionRequest tech) {
        List<DeploymentStepDto> steps = new ArrayList<>();
        int order = 0;

        // Phase 1: Local preparation (always)
        steps.add(step(order++, "Prepare your project locally",
                "Ensure your project builds and runs correctly on your local machine before attempting deployment.",
                "local", "Run your application locally and verify all features work",
                "Catching issues locally is much easier than debugging in production",
                "Local terminal / IDE",
                null, null,
                "Your app runs without errors on localhost",
                List.of("Port conflicts: change the port in your config",
                        "Missing dependencies: run install again",
                        "Database not running: start PostgreSQL/Docker first"),
                null));

        steps.add(step(order++, "Create safe environment-variable files",
                "Create .env files for local development with placeholder values. Never commit real secrets.",
                "local", "Create frontend/.env.local and backend environment configuration",
                "Separates config from code; prevents secrets from being committed",
                "Local project folder",
                "cp .env.example .env.local", "Copies the example env file to your local env file",
                "You have .env files with placeholder values",
                List.of(".env.example missing: create one with all required variable names"),
                "Never commit .env files with real values to Git"));

        steps.add(step(order++, "Verify .gitignore configuration",
                "Ensure sensitive files and build artifacts are excluded from version control.",
                "local", "Check that .env, node_modules, build/, and target/ are in .gitignore",
                "Prevents accidental commits of secrets and generated files",
                "Local terminal",
                "git status", "Shows which files Git is tracking",
                ".env files and build directories appear as untracked or ignored",
                List.of("File still shows in git status: add it to .gitignore and run git rm --cached"),
                "This is your primary defense against secret leaks"));

        // Phase 2: Build verification
        if (hasFrontend(tech)) {
            steps.add(step(order++, "Test the frontend production build",
                    "Create a production build of your frontend to catch build-time errors.",
                    "local", "Run the production build command for your frontend framework",
                    "Development and production builds can behave differently",
                    "Local terminal",
                    "npm ci && npm run build", "Installs exact dependencies and creates an optimized production build",
                    "Build completes with no errors; dist/ or build/ folder is created",
                    List.of("Build fails: check the error message, fix the issue, retry",
                            "Out of memory: increase Node memory with NODE_OPTIONS=--max-old-space-size=4096"),
                    null));
        }

        if (hasBackend(tech)) {
            steps.add(step(order++, "Test the backend production build",
                    "Build your backend JAR or distribution to verify it packages correctly.",
                    "local", "Run the backend build command (Maven or Gradle)",
                    "Ensures all dependencies are included and the app starts correctly",
                    "Local terminal",
                    "./mvnw clean package -DskipTests", "Cleans old builds, compiles code, and creates a JAR file",
                    "target/app.jar (or equivalent) is created successfully",
                    List.of("Build fails: check compilation errors in the output",
                            "Tests fail: run ./mvnw test separately to see which tests fail"),
                    null));
        }

        // Phase 3: GitHub
        steps.add(step(order++, "Create the GitHub repository",
                "Set up a remote repository on GitHub to store and version your code.",
                "github", "Go to github.com/new, name your repository, and create it (public or private)",
                "GitHub stores your code, enables collaboration, and triggers deployments",
                "GitHub website (github.com/new)",
                null, null,
                "You have a GitHub repository URL like https://github.com/USERNAME/REPO",
                List.of("Repository name taken: choose a different name",
                        "Need an account: sign up at github.com first"),
                "Use a clear, descriptive repository name"));

        steps.add(step(order++, "Push the code to GitHub",
                "Upload your local code to the GitHub repository.",
                "local", "Initialize Git (if needed), add remote, and push your code",
                "Creates a backup and enables deployment integrations",
                "Local terminal",
                "git init && git add . && git commit -m \"Initial commit\" && git branch -M main && git remote add origin https://github.com/USERNAME/REPO.git && git push -u origin main",
                "Initializes Git, stages all files, commits, and pushes to GitHub",
                "Code appears on GitHub; you can see your files in the browser",
                List.of("Authentication failed: use a personal access token or GitHub CLI",
                        "Remote already exists: run git remote remove origin first"),
                "Use HTTPS with a personal access token or SSH with a key"));

        // Phase 4: Database
        if (hasDatabase(tech)) {
            steps.add(step(order++, "Create the database project",
                    "Set up your production database on Supabase or your chosen provider.",
                    "database", "Create a new project on Supabase, select region, set a strong password",
                    "Your application needs a persistent data store in production",
                    "Supabase Dashboard (supabase.com) or Render Dashboard",
                null, null,
                    "Database project is created; you have connection details",
                    List.of("Password too weak: use a password generator",
                            "Region selection: choose the region closest to your users"),
                "Save the database password securely; you cannot view it again"));

            steps.add(step(order++, "Configure the production database connection",
                    "Add the production database URL and credentials to your backend environment.",
                    "backend", "Copy the connection string from Supabase and add it to Render env vars",
                    "Your backend needs to know where the production database is",
                    "Render Dashboard + Supabase Dashboard",
                "Add DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD to Render environment variables",
                "These values tell Spring Boot where to connect",
                    "Backend can connect to the database; migrations run successfully",
                    List.of("Connection refused: check the hostname and port",
                            "SSL error: add ?sslmode=require to the URL for Supabase"),
                "Never hardcode database credentials in your source code"));

            steps.add(step(order++, "Run database migrations",
                    "Apply your schema migrations to the production database.",
                    "backend", "Start your backend with Flyway enabled to auto-run migrations",
                    "Creates the required tables and indexes in production",
                    "Local terminal or Render logs",
                "SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run", "Starts the app in production mode",
                    "Flyway reports successful migration; tables are created",
                    List.of("Migration fails: check Flyway logs for SQL errors",
                            "Table already exists: you may need to baseline: flyway.baseline-on-migrate=true"),
                "Always test migrations on a copy of production data first"));
        }

        // Phase 5: Backend deployment
        if (hasBackend(tech)) {
            steps.add(step(order++, "Create the backend hosting service",
                    "Set up a web service on Render (or your chosen platform) to host your backend.",
                    "backend", "Create a new Web Service on Render, connect your GitHub repository",
                    "Provides a public URL for your backend API",
                    "Render Dashboard (dashboard.render.com)",
                null, null,
                    "Render service is created and shows in your dashboard",
                    List.of("Repository not found: ensure Render has GitHub access"),
                null));

            steps.add(step(order++, "Configure backend build and start commands",
                    "Tell Render how to build and run your Spring Boot application.",
                    "backend", "Set the root directory, build command, and start command",
                    "Render needs to know how to compile and launch your app",
                    "Render Dashboard > Service Settings",
                "Build: ./mvnw clean package -DskipTests\nStart: java -jar target/app.jar",
                "Build compiles the code; Start launches the JAR",
                    "Build succeeds; service shows as deployed",
                    List.of("Build fails: check Render logs for the specific error",
                            "Java version mismatch: ensure Render uses Java 17+"),
                null));

            steps.add(step(order++, "Add backend environment variables",
                    "Configure all required environment variables for the production backend.",
                    "backend", "Add DATABASE_URL, JWT_SECRET, GEMINI_API_KEY, FRONTEND_URL to Render",
                    "These values configure your application for production",
                    "Render Dashboard > Environment",
                null, null,
                    "All required variables are set; backend restarts",
                    List.of("Missing variable: the app will fail to start; check logs",
                            "JWT_SECRET too short: use at least 256 bits (64 hex chars)"),
                "JWT_SECRET must be strong and unique per environment"));

            steps.add(step(order++, "Deploy and test the backend",
                    "Verify your backend is running and responding to requests.",
                    "backend", "Open the Render service URL and test the health endpoint",
                    "Confirms the backend is accessible from the internet",
                    "Browser or terminal",
                "curl https://your-service.onrender.com/api/health", "Checks if the backend is responding",
                    "Returns {\"success\":true,\"data\":{\"status\":\"UP\"}}",
                    List.of("502 error: the app failed to start; check Render logs",
                            "404 error: check the context path and endpoint URL"),
                null));

            steps.add(step(order++, "Configure production CORS",
                    "Allow your frontend domain to make requests to your backend.",
                    "backend", "Set FRONTEND_URL to your Netlify/Vercel domain in Render env vars",
                    "Browsers block cross-origin requests unless explicitly allowed",
                    "Render Dashboard > Environment",
                "FRONTEND_URL=https://your-app.netlify.app", "Tells Spring Boot which origin to allow",
                    "Frontend can call backend APIs without CORS errors",
                    List.of("CORS still blocked: ensure no trailing slash; restart the service"),
                "Never use * (allow all origins) in production"));
        }

        // Phase 6: Frontend deployment
        if (hasFrontend(tech)) {
            steps.add(step(order++, "Create the frontend hosting site",
                    "Set up a site on Netlify (or your chosen platform) to host your frontend.",
                    "frontend", "Create a new site on Netlify, connect your GitHub repository",
                    "Provides a public URL for your frontend application",
                    "Netlify Dashboard (app.netlify.com)",
                null, null,
                    "Netlify site is created with a default URL",
                    List.of("Repository not found: authorize Netlify to access GitHub"),
                null));

            steps.add(step(order++, "Configure frontend build settings",
                    "Tell Netlify how to build your frontend and where the output is.",
                    "frontend", "Set the base directory, build command, and publish directory",
                    "Netlify needs to know how to compile your React app",
                    "Netlify Dashboard > Site Settings > Build & Deploy",
                "Base: frontend\nBuild: npm ci && npm run build\nPublish: frontend/dist",
                "Build installs deps and creates the production bundle",
                    "Build succeeds; site shows your frontend",
                    List.of("Build fails: check Netlify deploy logs",
                            "404 on refresh: add /* /index.html 200 redirect rule"),
                null));

            steps.add(step(order++, "Add frontend environment variables",
                    "Configure the API base URL so the frontend knows where your backend is.",
                    "frontend", "Add VITE_API_BASE_URL pointing to your Render backend URL",
                    "The frontend needs to know the backend API address",
                    "Netlify Dashboard > Site Settings > Environment Variables",
                "VITE_API_BASE_URL=https://your-backend.onrender.com/api", "Sets the API endpoint for all frontend requests",
                    "Frontend loads data from the backend; no localhost references",
                    List.of("API calls fail: check the URL has /api at the end",
                            "Mixed content error: ensure HTTPS is used for both frontend and backend"),
                "Never put API keys or secrets in frontend env vars"));

            steps.add(step(order++, "Configure SPA redirect rules",
                    "Fix the common issue where refreshing a page other than home returns 404.",
                    "frontend", "Add a redirect rule to serve index.html for all routes",
                    "React Router handles routing client-side; the server must serve index.html",
                    "Netlify Dashboard or netlify.toml",
                "Create netlify.toml with [[redirects]] from=\"/*\" to=\"/index.html\" status=200",
                "All URLs serve index.html, then React Router takes over",
                    "Refreshing any page works correctly",
                    List.of("Still 404: ensure the file is committed and pushed to GitHub"),
                null));
        }

        // Phase 7: Integration
        if (hasFrontend(tech) && hasBackend(tech)) {
            steps.add(step(order++, "Connect frontend to backend",
                    "Verify the frontend can communicate with the backend in production.",
                    "integration", "Test registration and login through the deployed frontend",
                    "End-to-end confirmation that both services work together",
                    "Browser (deployed frontend URL)",
                "Open the Netlify URL, try to register a new account",
                "Sends API requests to the Render backend",
                    "Account is created; you are logged in; dashboard loads",
                    List.of("Network error: check browser dev tools for CORS errors",
                            "500 error: check Render logs for backend exceptions"),
                null));

            steps.add(step(order++, "Test production API requests",
                    "Verify data flows correctly between frontend, backend, and database.",
                    "integration", "Create a project, generate a deployment plan, and mark a step complete",
                    "Confirms the full stack is working together",
                    "Browser (deployed frontend URL)",
                "Create a test project, run through the wizard, view the deployment plan",
                "Creates records in the database through the API",
                    "Project appears; deployment plan shows steps; progress is saved",
                    List.of("Data not persisting: check database connection and migrations",
                            "Authentication fails: check JWT secret is set correctly"),
                null));
        }

        // Phase 8: AI and extras
        if (hasAI(tech)) {
            steps.add(step(order++, "Configure AI API securely",
                    "Set up the Gemini API key on the backend only.",
                    "ai", "Add GEMINI_API_KEY to Render environment variables",
                    "The AI integration enables intelligent troubleshooting",
                    "Render Dashboard > Environment",
                "GEMINI_API_KEY=your-api-key-from-google-ai-studio", "Authenticates backend calls to Gemini",
                    "AI troubleshooting works when you submit an error",
                    List.of("Invalid key: generate a new key at makersuite.google.com"),
                "The Gemini key must NEVER be exposed in frontend code"));
        }

        // Phase 9: Final checks
        steps.add(step(order++, "Configure monitoring",
                "Set up health checks and logging to detect issues early.",
                "monitoring", "Verify /api/health endpoint works; check Render/Netlify dashboards",
                "You need visibility into your application's health",
                "Browser and dashboards",
                "curl https://your-backend.onrender.com/api/health", "Returns health status JSON",
                "Health endpoint responds; dashboards show deployment status",
                List.of("Health check fails: check backend logs for startup errors"),
                null));

        steps.add(step(order++, "Complete the security review",
                "Run through a final security checklist before considering deployment complete.",
                "security", "Verify: no secrets in code, .gitignore is correct, HTTPS is enforced, CORS is limited",
                "Security issues are much harder to fix after going live",
                "Security Centre in DeployPilot",
                null, null,
                "All security checks pass; no warnings in DeployPilot",
                List.of("Secret found in code: rotate the secret immediately, remove from history"),
                "Security is not a one-time task; review regularly"));

        return steps;
    }

    private static DeploymentStepDto step(int order, String title, String description, String category,
                                          String whatToDo, String whyNecessary, String whereToDoIt,
                                          String command, String whatCommandDoes, String expectedResult,
                                          List<String> commonErrors, String securityWarning) {
        DeploymentStepDto s = new DeploymentStepDto();
        s.setOrderIndex(order);
        s.setTitle(title);
        s.setDescription(description);
        s.setCategory(category);
        s.setWhatToDo(whatToDo);
        s.setWhyNecessary(whyNecessary);
        s.setWhereToDoIt(whereToDoIt);
        s.setCommandOrValue(command);
        s.setWhatCommandDoes(whatCommandDoes);
        s.setExpectedResult(expectedResult);
        s.setCommonErrors(commonErrors);
        s.setSecurityWarning(securityWarning);
        s.setStatus(StepStatus.NOT_STARTED);
        s.setCompletionControls("mark-complete,mark-blocked,skip,bookmark,add-note");
        return s;
    }

    private static boolean hasFrontend(TechnologySelectionRequest tech) {
        return tech.getFrontendTech() != null && !"none".equals(tech.getFrontendTech());
    }

    private static boolean hasBackend(TechnologySelectionRequest tech) {
        return tech.getBackendTech() != null && !"none".equals(tech.getBackendTech());
    }

    private static boolean hasDatabase(TechnologySelectionRequest tech) {
        return tech.getDatabase() != null && !"none".equals(tech.getDatabase());
    }

    private static boolean hasAI(TechnologySelectionRequest tech) {
        return tech.getAdditionalServices() != null &&
                (tech.getAdditionalServices().contains("gemini") || tech.getAdditionalServices().contains("openai"));
    }
}
