package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.model.User;
import com.deploypilot.model.enums.UserRole;
import com.deploypilot.repository.UserRepository;
import com.deploypilot.security.JwtTokenProvider;
import com.deploypilot.security.UserDetailsServiceImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authManager;
    private final UserDetailsServiceImpl userDetailsService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, AuthenticationManager authManager,
                       UserDetailsServiceImpl userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authManager = authManager;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public ApiResponse<AuthResponse> register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ApiResponse.error("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.USER);

        User saved = userRepository.save(user);
        String token = tokenProvider.generateToken(saved.getId(), saved.getUsername(), saved.getRole().name());
        UserResponse userDto = new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRole(), saved.getCreatedAt());

        return ApiResponse.ok("Registration successful", new AuthResponse(token, userDto));
    }

    public ApiResponse<AuthResponse> login(LoginRequest request) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ApiResponse.error("Invalid username or password");
        }

        User user = userDetailsService.loadAppUserByUsername(request.getUsername());
        String token = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        UserResponse userDto = new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getCreatedAt());

        return ApiResponse.ok("Login successful", new AuthResponse(token, userDto));
    }

    public ApiResponse<UserResponse> getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ApiResponse.error("Not authenticated");
        }
        String username = auth.getName();
        User user = userDetailsService.loadAppUserByUsername(username);
        UserResponse dto = new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getCreatedAt());
        return ApiResponse.ok(dto);
    }

    @Transactional
    public ApiResponse<Void> updatePassword(PasswordUpdateRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userDetailsService.loadAppUserByUsername(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            return ApiResponse.error("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return ApiResponse.ok("Password updated successfully", null);
    }
}
