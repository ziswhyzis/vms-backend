package com.starkgroup.vms.auth;

import com.starkgroup.vms.error.DuplicateUsernameException;
import com.starkgroup.vms.error.InvalidCredentialsException;
import com.starkgroup.vms.error.ResourceNotFoundException;
import com.starkgroup.vms.security.JwtService;
import com.starkgroup.vms.user.User;
import com.starkgroup.vms.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));
        return issueToken(user);
    }

    /**
     * Creates an operator account and signs it in immediately, so a fresh
     * deployment is usable even before any account has been seeded.
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUsernameException(username);
        }
        User user = new User(
                username,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                "ADMIN"
        );
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // The unique index is the final guarantee if two requests race.
            throw new DuplicateUsernameException(username);
        }
        return issueToken(user);
    }

    private LoginResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getUsername());
        return new LoginResponse(token, jwtService.expirationInstant(), UserResponse.from(user));
    }
}
