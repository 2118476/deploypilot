package com.deploypilot.util;

import com.deploypilot.exception.UnauthorizedAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class CurrentUserUtil {
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedAccessException("No authenticated user in security context");
    }
}
