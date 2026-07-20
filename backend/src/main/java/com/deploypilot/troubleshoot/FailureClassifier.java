package com.deploypilot.troubleshoot;

import com.deploypilot.troubleshoot.StructuredTroubleshooting.Cause;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.Confidence;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.Evidence;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.Fact;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.RetryAdvice;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.Status;
import com.deploypilot.troubleshoot.StructuredTroubleshooting.Step;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, server-side classifier for known deployment failures. Given a
 * sanitised {@link TroubleshootingContext} it returns exactly one
 * {@link StructuredTroubleshooting} — this is ground truth. Gemini may only
 * re-word the summary and explanations; it can never change the error code, the
 * retry-safety verdict or the status this class sets.
 *
 * <p>The classifier is written in plain, beginner-friendly language and never
 * asks the user to paste tokens, passwords or environment-variable values.
 */
@Component
public class FailureClassifier {

    private static final String SECRET_WARNING =
        "Do not paste tokens, passwords, API keys or environment-variable values. A screenshot of the status or the first failed line is enough.";

    public StructuredTroubleshooting classify(TroubleshootingContext ctx) {
        TroubleshootingErrorCode code = detect(ctx);
        StructuredTroubleshooting s = base(ctx, code);
        switch (code) {
            case NETLIFY_HOST_KEY -> hostKey(ctx, s);
            case NETLIFY_AUTH_401 -> netlifyAuth(ctx, s);
            case NETLIFY_FORBIDDEN_403 -> netlifyForbidden(ctx, s);
            case NETLIFY_BUILD_FAILED -> netlifyBuild(ctx, s);
            case NETLIFY_PUBLISH_DIR -> publishDir(ctx, s);
            case FRONTEND_ENV_MISSING -> frontendEnv(ctx, s);
            case RENDER_DOCKERFILE_MISSING -> renderDockerfile(ctx, s);
            case RENDER_BUILD_FAILED -> renderBuild(ctx, s);
            case RENDER_COLD_START -> coldStart(ctx, s);
            case BACKEND_HEALTH_FAILED -> backendHealth(ctx, s);
            case CORS_WRONG_ORIGIN -> cors(ctx, s);
            case DUPLICATE_API_PATH -> duplicateApi(ctx, s);
            case SUPABASE_CONNECTION -> supabase(ctx, s);
            case MISSING_SECRET -> missingSecret(ctx, s);
            case VERIFICATION_INCONCLUSIVE -> verificationInconclusive(ctx, s);
            case VERSION_MISMATCH -> versionMismatch(ctx, s);
            case UNKNOWN -> unknown(ctx, s);
        }
        return s;
    }

    // ==================== detection (ground truth) ====================

    TroubleshootingErrorCode detect(TroubleshootingContext ctx) {
        String t = ctx.combinedFailureText().toLowerCase(Locale.ROOT);
        String provider = ctx.getFailedStepProvider() == null ? "" : ctx.getFailedStepProvider().toUpperCase(Locale.ROOT);
        String stepId = ctx.getFailedStepId() == null ? "" : ctx.getFailedStepId().toLowerCase(Locale.ROOT);

        // Netlify repository-clone authorisation — the JobPilot case. Most specific first.
        if (containsAny(t, "host key verification failed", "could not read from remote repository")
            || (t.contains("preparing repo") && t.contains("exit status 128"))
            || (t.contains("permission denied") && t.contains("publickey"))) {
            return TroubleshootingErrorCode.NETLIFY_HOST_KEY;
        }
        if (containsAny(t, "/api/api")) return TroubleshootingErrorCode.DUPLICATE_API_PATH;
        if (containsAny(t, "access-control-allow-origin", "blocked by cors", "cors policy", "not allowed by access-control")) {
            return TroubleshootingErrorCode.CORS_WRONG_ORIGIN;
        }
        // Auth / permission before generic "build failed".
        if (isNetlify(provider, stepId) && containsAny(t, "401", "unauthorized", "rejected the token", "invalid token", "token has expired")) {
            return TroubleshootingErrorCode.NETLIFY_AUTH_401;
        }
        if (isNetlify(provider, stepId) && containsAny(t, "403", "forbidden", "not allowed", "plan", "upgrade required", "permission")) {
            return TroubleshootingErrorCode.NETLIFY_FORBIDDEN_403;
        }
        if (containsAny(t, "dockerfile", "no such file") && t.contains("docker")) {
            return TroubleshootingErrorCode.RENDER_DOCKERFILE_MISSING;
        }
        if (containsAny(t, "publish directory", "publish path", "page not found", "no such directory", "dist not found", "directory does not exist")) {
            return TroubleshootingErrorCode.NETLIFY_PUBLISH_DIR;
        }
        // Missing frontend env: explicit "setup required", or the frontend env step with unresolved VITE_ vars.
        if (containsAny(t, "vite_supabase", "vite_api", "setup required", "missing its authentication configuration")
            || (stepId.contains("frontend.env") && containsAny(t, "no value", "missing", "required"))) {
            return TroubleshootingErrorCode.FRONTEND_ENV_MISSING;
        }
        if (isSupabase(provider, stepId) || containsAny(t, "supabase")) {
            if (containsAny(t, "could not connect", "connection refused", "password authentication failed",
                "pooler", "timeout", "auth", "sql error", "database")) {
                return TroubleshootingErrorCode.SUPABASE_CONNECTION;
            }
        }
        if (containsAny(t, "health check", "health endpoint", "/health", "service unhealthy")) {
            return TroubleshootingErrorCode.BACKEND_HEALTH_FAILED;
        }
        if (isRender(provider, stepId) && containsAny(t, "timed out", "timeout", "cold start", "did not reach a live state", "502", "took too long")) {
            return TroubleshootingErrorCode.RENDER_COLD_START;
        }
        if (isRender(provider, stepId) && containsAny(t, "build failed", "build error", "compilation", "non-zero exit")) {
            return TroubleshootingErrorCode.RENDER_BUILD_FAILED;
        }
        if (isNetlify(provider, stepId) && containsAny(t, "build failed", "command failed", "npm err", "non-zero exit", "build exited")) {
            return TroubleshootingErrorCode.NETLIFY_BUILD_FAILED;
        }
        // Missing required secret — driven by verified records, not text.
        if (!ctx.getMissingRequiredSecrets().isEmpty()
            && (stepId.contains("env") || stepId.contains("deploy") || provider.equals("RENDER") || provider.equals("NETLIFY"))) {
            return TroubleshootingErrorCode.MISSING_SECRET;
        }
        if ("INCONCLUSIVE".equalsIgnoreCase(ctx.getVerificationStatus())
            || "UNKNOWN".equalsIgnoreCase(ctx.getVerificationStatus())
            || containsAny(t, "false positive", "inconclusive")) {
            return TroubleshootingErrorCode.VERIFICATION_INCONCLUSIVE;
        }
        if (containsAny(t, "version mismatch", "outdated commit", "stale build", "commit does not match", "old version")) {
            return TroubleshootingErrorCode.VERSION_MISMATCH;
        }
        return TroubleshootingErrorCode.UNKNOWN;
    }

    // ==================== per-code diagnoses ====================

    /**
     * The JobPilot host-key case with Case A / Case B branching driven by recorded
     * evidence — never blames application source, never auto-unlinks, never repeats
     * "relink" once the user reports they already did.
     */
    private void hostKey(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Netlify could not download your GitHub repository, so your application code never started building. "
            + "This is a repository connection (authorisation) problem between Netlify and GitHub — it is not a bug in your code.");
        s.getVerifiedFacts().add(new Fact("automation.step.frontend.log",
            "The failed step stopped while 'preparing repo' with 'Host key verification failed' — Netlify could not read the repository."));
        s.getLikelyCauses().add(new Cause(
            "Netlify's connection to your GitHub repository is not authorised (a stale or missing GitHub link).",
            Confidence.LIKELY.name(),
            "The clone failed before any build step ran, which points at repository access, not the code."));

        boolean relinked = ctx.isRelinkReportedByUser();
        String manual = ctx.getManualDeployResult() == null ? "UNKNOWN" : ctx.getManualDeployResult();

        if (!relinked) {
            // First-time guidance: recommend a relink, then ask for Netlify's own deploy result.
            s.setStatus(Status.NEEDS_EVIDENCE);
            s.setConfidence(Confidence.LIKELY);
            s.getSteps().add(new Step(1,
                "In Netlify, open your site 'jobpilot-frontend' → Site configuration → Build & deploy → Continuous deployment → 'Manage repository', and link/relink to your GitHub repository through the GitHub connection.",
                "Netlify", "Netlify shows the repository connected via the GitHub App.", false));
            s.getSteps().add(new Step(2,
                "Then open Netlify → jobpilot-frontend → Deploys and trigger (or watch) Netlify's own deploy. Note whether Netlify's OWN deploy succeeds or fails.",
                "Netlify", "You can see whether Netlify itself can now build the repository.", false));
            s.getSteps().add(new Step(3,
                "Come back and tell me the result of Netlify's own deploy. Do not click DeployPilot's Retry yet.",
                "DeployPilot", "We use that result to tell an authorisation failure apart from a trigger problem.", false));
            s.getRequiredEvidence().add(new Evidence(
                "Did Netlify's own deployment succeed or fail after relinking?",
                "It separates a still-broken GitHub authorisation (Netlify also fails) from a DeployPilot trigger issue (Netlify succeeds).",
                SECRET_WARNING));
            s.setRetryAdvice(new RetryAdvice(false,
                "Retry will hit the same clone error until Netlify can read the repository. Relink first, then check Netlify's own deploy."));
            return;
        }

        // The user already relinked. Do NOT tell them to relink again — branch on evidence.
        if ("FAILED".equalsIgnoreCase(manual)) {
            // Case A: Netlify's own deploy also fails → authorisation is still broken.
            s.setStatus(Status.NEEDS_EVIDENCE);
            s.setConfidence(Confidence.LIKELY);
            s.setSummary(s.getSummary() + " You relinked, but Netlify's OWN deployment still fails — so the GitHub authorisation itself is still broken (Case A).");
            s.getLikelyCauses().add(new Cause(
                "The GitHub App authorisation for this Netlify site or team is still incomplete or lacks access to this repository.",
                Confidence.LIKELY.name(),
                "If Netlify's own build cannot clone the repo, DeployPilot's trigger cannot either."));
            s.getSteps().add(new Step(1,
                "In Netlify, open Site configuration → Build & deploy → Manage repository, and re-authorise the GitHub connection for THIS repository (check the GitHub App has access to it under your GitHub → Settings → Applications → Netlify).",
                "Netlify / GitHub", "GitHub lists this repository under the Netlify app's repository access.", false));
            s.getSteps().add(new Step(2,
                "Trigger Netlify's own deploy again and confirm it can now clone the repository before returning to DeployPilot.",
                "Netlify", "Netlify's own deploy reaches the build step (past 'preparing repo').", false));
            s.getRequiredEvidence().add(new Evidence(
                "After re-authorising, does Netlify's own deploy get past 'preparing repo'?",
                "Confirms whether GitHub access is finally granted before we retry from DeployPilot.",
                SECRET_WARNING));
            s.setRetryAdvice(new RetryAdvice(false,
                "Netlify itself still cannot clone the repository, so a DeployPilot retry will fail the same way. Fix GitHub access first."));
            return;
        }
        if ("SUCCEEDED".equalsIgnoreCase(manual)) {
            // Case B: Netlify's own deploy succeeds but DeployPilot's trigger failed.
            s.setStatus(Status.READY_TO_RETRY);
            s.setConfidence(Confidence.LIKELY);
            s.setSummary(s.getSummary() + " You relinked and Netlify's OWN deployment now succeeds (Case B) — the GitHub authorisation is fixed. The earlier failure was on the deploy DeployPilot triggered.");
            s.getLikelyCauses().add(new Cause(
                "The GitHub authorisation is now valid; the previous error was recorded before the relink took effect.",
                Confidence.LIKELY.name(),
                "Netlify can now build the same repository on its own."));
            s.getSteps().add(new Step(1,
                "Click 'Retry from the failed step' in DeployPilot. It resumes at this step, reuses the same site, and will not unlink your GitHub connection.",
                "DeployPilot", "The frontend step gets past 'preparing repo' and starts building.", true));
            s.getSteps().add(new Step(2,
                "If DeployPilot's retry still fails at the same step while Netlify's own deploy works, tell me — we will compare the site DeployPilot targets with the one that built.",
                "DeployPilot", "Either the retry builds, or we have a concrete trigger/resource mismatch to investigate.", false));
            s.setRetryAdvice(new RetryAdvice(true,
                "Netlify can now clone the repository on its own, so retrying from the failed step is the correct next action."));
            return;
        }
        // Relinked but the result of Netlify's own deploy is unknown — ask for it, do not relink again.
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.setSummary(s.getSummary() + " You reported relinking, but we still need the result of Netlify's OWN deployment before recommending anything else.");
        s.getSteps().add(new Step(1,
            "Do not relink again yet. Open Netlify → jobpilot-frontend → Deploys → the latest deployment and read the first failed stage (or confirm it succeeded).",
            "Netlify", "You can see whether Netlify's own build clones the repository successfully.", false));
        s.getSteps().add(new Step(2,
            "Tell me whether Netlify's own deployment SUCCEEDED or FAILED. That decides the next move.",
            "DeployPilot", "We branch to the right fix instead of repeating the relink.", false));
        s.getRequiredEvidence().add(new Evidence(
            "Open Netlify → jobpilot-frontend → Deploys → latest deployment and provide the first failed stage (or confirm success).",
            "We need Netlify's own result to distinguish a broken GitHub authorisation from a DeployPilot trigger issue.",
            SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false,
            "Until we know whether Netlify's own deploy works, retrying could just repeat the same failure."));
    }

    private void netlifyAuth(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Netlify rejected the access token DeployPilot used. Your application is fine — DeployPilot could not sign in to Netlify.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.LIKELY);
        s.getLikelyCauses().add(new Cause("The Netlify token is expired, revoked or was pasted incorrectly.",
            Confidence.LIKELY.name(), "Netlify returned an authentication (401) response."));
        s.getSteps().add(new Step(1, "In DeployPilot, open Connections → Netlify and reconnect Netlify with a current personal access token.",
            "DeployPilot", "Netlify shows as connected again.", false));
        s.getSteps().add(new Step(2, "Then retry from the failed step.", "DeployPilot", "The step gets past authentication.", true));
        s.getRequiredEvidence().add(new Evidence("Confirm the Netlify connection status in DeployPilot → Connections.",
            "Tells us whether the token was refreshed.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Reconnect Netlify first; retrying with the same rejected token will fail again."));
    }

    private void netlifyForbidden(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Netlify accepted the token but blocked this specific action — usually a permission or plan restriction, not a bad token.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.LIKELY);
        s.getLikelyCauses().add(new Cause("The Netlify account lacks permission for this action, or a paid-plan feature is required.",
            Confidence.LIKELY.name(), "Netlify returned a 403 (forbidden), which is about permissions, not credentials."));
        s.getSteps().add(new Step(1, "Open the failed step's provider response shown below and read what Netlify said was not allowed.",
            "DeployPilot", "You can see the specific permission or feature Netlify blocked.", false));
        s.getSteps().add(new Step(2, "Confirm your Netlify account/team has access to this site and does not require a paid plan for this action.",
            "Netlify", "You confirm the account can perform the action.", false));
        s.getRequiredEvidence().add(new Evidence("What did Netlify's response say was forbidden? (the message only, no tokens)",
            "The exact restriction decides whether it is a role or a plan limit.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Retrying repeats the same permission error until the account permission or plan is changed."));
    }

    private void netlifyBuild(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Netlify downloaded your repository but the build command failed. Your code reached the build step and stopped there.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("The build command failed — a missing dependency, a script error, or a wrong build command.",
            Confidence.POSSIBLE.name(), "The failure happened during the build stage, after the repository was cloned."));
        s.getSteps().add(new Step(1, "Open Netlify → your site → Deploys → the failed deploy and read the first red error line in the build log.",
            "Netlify", "You can see the exact build error.", false));
        s.getSteps().add(new Step(2, "Confirm the build command (for Vite this is usually 'npm run build') and that the app builds locally.",
            "Netlify / your machine", "The build command is correct and succeeds locally.", false));
        s.getRequiredEvidence().add(new Evidence("The first failed line from Netlify's build log.",
            "It names the exact build error to fix.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Fix the build error first; retrying the same build will fail the same way."));
    }

    private void publishDir(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The build finished but Netlify could not find the folder of files to publish (the publish directory).");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("The configured publish directory does not match your build output (Vite outputs to 'dist').",
            Confidence.POSSIBLE.name(), "Netlify reported it could not find the publish directory after the build."));
        s.getSteps().add(new Step(1, "In Netlify → Site configuration → Build & deploy, set the publish directory to your build output folder (for Vite: 'dist').",
            "Netlify", "The publish directory matches the build output.", false));
        s.getRequiredEvidence().add(new Evidence("Which publish directory is configured in Netlify?",
            "Confirms it matches the build output folder.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Correct the publish directory first, then retry."));
    }

    private void frontendEnv(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        List<String> missing = new ArrayList<>();
        for (String m : ctx.getMissingRequiredSecrets()) if (m.toUpperCase(Locale.ROOT).startsWith("VITE_")) missing.add(m);
        s.setSummary("The frontend deployed but is missing its configuration values, so it shows a 'Setup required' screen instead of the app.");
        s.setStatus(Status.DIAGNOSED);
        s.setConfidence(missing.isEmpty() ? Confidence.LIKELY : Confidence.CONFIRMED);
        s.getLikelyCauses().add(new Cause("Required frontend variables (VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY, VITE_API_URL) are not set on Netlify.",
            missing.isEmpty() ? Confidence.LIKELY.name() : Confidence.CONFIRMED.name(),
            "The frontend needs these public values baked in at build time."));
        s.getSteps().add(new Step(1, "Let DeployPilot set the frontend variables: retry from the 'Set frontend environment variables' step. VITE_API_URL comes from your deployed backend URL.",
            "DeployPilot", "The three VITE_ variables are set on Netlify and a new build starts.", true));
        s.getSteps().add(new Step(2, "After the new build, reload the site — the 'Setup required' screen should be replaced by your app.",
            "Browser", "The app loads instead of the setup screen.", false));
        s.getRequiredEvidence().add(new Evidence("None required — DeployPilot provides these values. (Never paste the anon key or any value here.)",
            "These are provisioned from your deployment records.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(true, "Retrying the frontend environment step is the correct fix; DeployPilot sets the values safely (never the service-role key)."));
    }

    private void renderDockerfile(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Render could not find a Dockerfile to build your backend.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("The Dockerfile is missing or its path is wrong for the selected root directory.",
            Confidence.POSSIBLE.name(), "Render reported it could not find the Dockerfile."));
        s.getSteps().add(new Step(1, "Confirm a Dockerfile exists at the backend root and that DeployPilot's suggested Dockerfile was merged.",
            "GitHub", "A Dockerfile is present at the expected path.", false));
        s.getRequiredEvidence().add(new Evidence("Does a Dockerfile exist in the backend directory of the repository?",
            "Confirms whether the file is missing or mis-pathed.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Add the Dockerfile first, then retry."));
    }

    private void renderBuild(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Render started building your backend but the build failed.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("A compile or dependency error during the backend build.",
            Confidence.POSSIBLE.name(), "The failure was during Render's build stage."));
        s.getSteps().add(new Step(1, "Open Render → your service → Logs and read the first build error line.",
            "Render", "You can see the exact build error.", false));
        s.getRequiredEvidence().add(new Evidence("The first failed line from Render's build log.",
            "It names the exact backend build error.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Fix the build error first; retrying rebuilds the same code."));
    }

    private void coldStart(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The backend took too long to respond. Free Render instances 'sleep' when idle, so the first request after inactivity can be slow.");
        s.setStatus(Status.READY_TO_RETRY);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("A cold start on a free Render instance, or the service is still starting.",
            Confidence.POSSIBLE.name(), "The step timed out waiting for the backend to become live."));
        s.getSteps().add(new Step(1, "Wait about a minute for the backend to wake up, then retry from the failed step.",
            "DeployPilot", "The backend responds and the step completes.", true));
        s.getRequiredEvidence().add(new Evidence("Does opening the backend URL in a browser eventually return a response?",
            "Confirms the service is up but was just slow to start.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(true, "A single retry after a short wait is reasonable for a cold start."));
    }

    private void backendHealth(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The backend is deployed but its health endpoint did not respond as healthy, so DeployPilot cannot confirm it is up.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("The service crashed on startup, or the health-check path is wrong.",
            Confidence.POSSIBLE.name(), "The health check did not return a healthy response."));
        s.getSteps().add(new Step(1, "Open Render → your service → Logs and check whether the app started or crashed on boot.",
            "Render", "You can see whether the service is running.", false));
        s.getSteps().add(new Step(2, "Confirm the health-check path matches your API's health route (for example /api/health).",
            "Render", "The health-check path is correct.", false));
        s.getRequiredEvidence().add(new Evidence("Did the backend start cleanly in Render's logs, or did it crash?",
            "Separates a crash from a wrong health path.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Confirm the service is actually running before retrying."));
    }

    private void cors(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The frontend loads but the backend is blocking its requests because the allowed origin (CORS) does not match the frontend URL.");
        s.setStatus(Status.DIAGNOSED);
        s.setConfidence(Confidence.LIKELY);
        s.getLikelyCauses().add(new Cause("The backend's allowed frontend origin does not exactly match the deployed frontend URL.",
            Confidence.LIKELY.name(), "The browser reported a cross-origin (CORS) block."));
        s.getSteps().add(new Step(1, "Retry the 'Set backend allowed frontend origin' step so DeployPilot sets the backend's allowed origin to the exact frontend URL, then restarts the backend.",
            "DeployPilot", "The backend allows the frontend origin and is restarted.", true));
        s.getRequiredEvidence().add(new Evidence("None required — DeployPilot uses the recorded frontend URL.",
            "The origin comes from your deployment records.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(true, "Retrying the CORS step sets the correct origin and restarts the backend."));
    }

    private void duplicateApi(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("Requests are going to a doubled '/api/api' path, so the backend returns not-found. The API base URL already includes '/api'.");
        s.setStatus(Status.DIAGNOSED);
        s.setConfidence(Confidence.LIKELY);
        s.getLikelyCauses().add(new Cause("VITE_API_URL includes '/api' and the frontend code also adds '/api', producing '/api/api'.",
            Confidence.LIKELY.name(), "The observed request path contains '/api/api'."));
        s.getSteps().add(new Step(1, "Make sure VITE_API_URL is the backend base URL. If your frontend already adds '/api' in code, VITE_API_URL should NOT also end in '/api'.",
            "DeployPilot / code", "Requests hit '/api/...' once, not '/api/api/...'.", false));
        s.getRequiredEvidence().add(new Evidence("Does your frontend code add '/api' to requests itself?",
            "Decides whether VITE_API_URL should include '/api' or not.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Fix the doubled path first, then retry the frontend environment step."));
    }

    private void supabase(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The backend could not connect to or authenticate with the Supabase database.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("A wrong database connection value, or the IPv4 session pooler is needed on Render.",
            Confidence.POSSIBLE.name(), "The failure mentions a database/Supabase connection problem."));
        s.getSteps().add(new Step(1, "Confirm the Supabase project is active and healthy, and that the backend uses the session pooler connection on Render.",
            "Supabase / Render", "The Supabase project is healthy and the connection type is correct.", false));
        s.getRequiredEvidence().add(new Evidence("Is the Supabase project ACTIVE_HEALTHY? (status only, never the password or keys)",
            "Confirms the database side is up before checking the connection string.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Confirm the database is healthy and the connection type is correct before retrying."));
    }

    private void missingSecret(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        String names = String.join(", ", ctx.getMissingRequiredSecrets());
        s.setSummary("A required configuration value has not been provided yet, so the deployment cannot continue.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.CONFIRMED);
        s.getVerifiedFacts().add(new Fact("blueprint.missing_required_secrets",
            "DeployPilot's records show these required values are not set: " + names + "."));
        s.getLikelyCauses().add(new Cause("A required secret named above has no stored value.",
            Confidence.CONFIRMED.name(), "DeployPilot tracks which required values are still missing."));
        s.getSteps().add(new Step(1, "In DeployPilot, provide the missing value(s) for: " + names + ". DeployPilot stores them encrypted; never commit them.",
            "DeployPilot", "The required values are stored and the step can proceed.", false));
        s.getRequiredEvidence().add(new Evidence("Provide the missing value(s) through DeployPilot's secure input — never in this chat.",
            "DeployPilot encrypts stored values; the chat must never contain them.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Provide the missing value(s) first, then retry."));
    }

    private void verificationInconclusive(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The final verification could not clearly confirm the deployment is healthy. This does not prove it failed — the result is inconclusive.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("A check could not run reliably (for example a slow cold start), so the result is unknown rather than failed.",
            Confidence.POSSIBLE.name(), "Verification reported an inconclusive/unknown result."));
        s.getSteps().add(new Step(1, "Open the frontend and backend URLs yourself and confirm the app loads and responds.",
            "Browser", "You can see whether the deployment actually works.", false));
        s.getSteps().add(new Step(2, "Re-run verification once the services are warm.", "DeployPilot", "Verification returns a clear result.", false));
        s.getRequiredEvidence().add(new Evidence("Does the deployed frontend load and can it reach the backend?",
            "A direct check resolves an inconclusive automated result.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Check the live URLs first; an inconclusive result is not proof of failure."));
    }

    private void versionMismatch(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("The deployed version looks older than the code you expect — the latest changes may not be live yet.");
        s.setStatus(Status.NEEDS_EVIDENCE);
        s.setConfidence(Confidence.POSSIBLE);
        s.getLikelyCauses().add(new Cause("An earlier build is still serving, or a new deploy has not finished.",
            Confidence.POSSIBLE.name(), "The reported commit does not match the expected one."));
        s.getSteps().add(new Step(1, "Trigger a fresh deploy and wait for it to finish, then reload with a hard refresh.",
            "DeployPilot / Browser", "The live version matches the expected commit.", false));
        s.getRequiredEvidence().add(new Evidence("Which commit does the live site report versus the one you expect?",
            "Confirms whether a new deploy is needed.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(true, "Re-deploying to publish the latest commit is reasonable."));
    }

    private void unknown(TroubleshootingContext ctx, StructuredTroubleshooting s) {
        s.setSummary("DeployPilot does not yet have a confident match for this failure. Here is what is proven, and what to collect next.");
        s.setStatus(Status.UNKNOWN);
        s.setConfidence(Confidence.UNKNOWN);
        s.getLikelyCauses().add(new Cause("Not enough evidence to name a specific cause yet.",
            Confidence.UNKNOWN.name(), "The failure text did not match a known pattern."));
        s.getSteps().add(new Step(1, "Open the failed step's provider log (below) and read the first error line.",
            "DeployPilot", "You find the first concrete error message.", false));
        s.getSteps().add(new Step(2, "Share that first error line (no tokens or values) so we can classify it.",
            "DeployPilot", "We can match the failure to a known cause.", false));
        s.getRequiredEvidence().add(new Evidence("The first error line from the failed step's provider log.",
            "It lets us classify an otherwise-unknown failure.", SECRET_WARNING));
        s.setRetryAdvice(new RetryAdvice(false, "Collect the first error line before retrying blindly."));
    }

    // ==================== helpers ====================

    private StructuredTroubleshooting base(TroubleshootingContext ctx, TroubleshootingErrorCode code) {
        StructuredTroubleshooting s = new StructuredTroubleshooting();
        s.setErrorCode(code.name());
        s.setProvider(ctx.getFailedStepProvider() != null && !ctx.getFailedStepProvider().isBlank()
            ? ctx.getFailedStepProvider() : code.provider());
        if (ctx.getFailedStepTitle() != null) {
            s.getVerifiedFacts().add(new Fact("automation.step.failed",
                "The step '" + ctx.getFailedStepTitle() + "' is the one that failed."));
        }
        if (ctx.getFailureReason() != null && !ctx.getFailureReason().isBlank()) {
            s.getVerifiedFacts().add(new Fact("automation.run.failure_reason",
                "DeployPilot recorded: " + ctx.getFailureReason()));
        }
        for (String d : ctx.getProviderDiagnostics()) {
            s.getVerifiedFacts().add(new Fact("provider.diagnostic", d));
        }
        return s;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private static boolean isNetlify(String provider, String stepId) {
        return provider.contains("NETLIFY") || stepId.contains("frontend");
    }

    private static boolean isRender(String provider, String stepId) {
        return provider.contains("RENDER") || stepId.contains("backend");
    }

    private static boolean isSupabase(String provider, String stepId) {
        return provider.contains("SUPABASE") || stepId.contains("database") || stepId.contains("supabase");
    }
}
