package com.deploypilot.exception;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.repoaccess.RepositoryAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedAccessException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(com.deploypilot.verify.SafeUrlException.class)
    public ResponseEntity<ApiResponse<Void>> handleSafeUrl(com.deploypilot.verify.SafeUrlException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RepositoryAccessException.NotFound.class)
    public ResponseEntity<ApiResponse<Void>> handleRepoNotFound(RepositoryAccessException.NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RepositoryAccessException.AccessDenied.class)
    public ResponseEntity<ApiResponse<Void>> handleRepoAccessDenied(RepositoryAccessException.AccessDenied e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(RepositoryAccessException.RateLimited.class)
    public ResponseEntity<ApiResponse<Void>> handleRepoRateLimited(RepositoryAccessException.RateLimited e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(e.getMessage()));
    }

    // BadCredentials deliberately maps to 502, not 401: a 401 would make the
    // frontend log the user out, but the problem is the server's GitHub token.
    @ExceptionHandler(RepositoryAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleRepoAccess(RepositoryAccessException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }

    // A rejected provider token is the user's input problem (400), not a
    // DeployPilot auth problem (401 would log them out).
    @ExceptionHandler(com.deploypilot.provider.ProviderException.BadCredentials.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderBadCredentials(
            com.deploypilot.provider.ProviderException.BadCredentials e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(com.deploypilot.provider.ProviderException.NotFound.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderNotFound(
            com.deploypilot.provider.ProviderException.NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(com.deploypilot.provider.ProviderException.BillingRequired.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderBilling(
            com.deploypilot.provider.ProviderException.BillingRequired e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(com.deploypilot.provider.ProviderException.RateLimited.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderRateLimited(
            com.deploypilot.provider.ProviderException.RateLimited e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(com.deploypilot.provider.ProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleProvider(com.deploypilot.provider.ProviderException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
