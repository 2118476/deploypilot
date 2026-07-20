package com.deploypilot.troubleshoot;

/**
 * The deterministic failure taxonomy DeployPilot recognises. The
 * {@link FailureClassifier} maps a sanitised failure to exactly one of these
 * codes; it is ground truth. Gemini may explain a code but must never invent or
 * contradict one.
 */
public enum TroubleshootingErrorCode {
    NETLIFY_HOST_KEY,              // repository clone authorisation ("Host key verification failed")
    NETLIFY_AUTH_401,             // Netlify rejected the token
    NETLIFY_FORBIDDEN_403,        // permission or plan restriction
    NETLIFY_BUILD_FAILED,         // build command failed
    NETLIFY_PUBLISH_DIR,          // wrong publish directory
    FRONTEND_ENV_MISSING,         // required VITE_ variables not set
    RENDER_DOCKERFILE_MISSING,    // Dockerfile not found
    RENDER_BUILD_FAILED,          // backend build failed
    RENDER_COLD_START,            // free instance cold start / timeout
    BACKEND_HEALTH_FAILED,        // health endpoint not responding
    CORS_WRONG_ORIGIN,            // backend rejects the frontend origin
    DUPLICATE_API_PATH,           // duplicated /api/api path
    SUPABASE_CONNECTION,          // Supabase connection/auth failure
    MISSING_SECRET,               // a required secret value is not provided
    VERIFICATION_INCONCLUSIVE,    // verification false positive / inconclusive
    VERSION_MISMATCH,             // deployed commit is outdated / mismatched
    UNKNOWN;                      // no confident deterministic match

    public String provider() {
        return switch (this) {
            case NETLIFY_HOST_KEY, NETLIFY_AUTH_401, NETLIFY_FORBIDDEN_403,
                 NETLIFY_BUILD_FAILED, NETLIFY_PUBLISH_DIR, FRONTEND_ENV_MISSING -> "NETLIFY";
            case RENDER_DOCKERFILE_MISSING, RENDER_BUILD_FAILED, RENDER_COLD_START,
                 BACKEND_HEALTH_FAILED, CORS_WRONG_ORIGIN, DUPLICATE_API_PATH -> "RENDER";
            case SUPABASE_CONNECTION -> "SUPABASE";
            default -> "DEPLOYPILOT";
        };
    }
}
