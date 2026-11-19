package com.starkgroup.vms.auth;

import com.starkgroup.vms.error.InvalidCredentialsException;
import com.starkgroup.vms.error.ResourceNotFoundException;
import com.starkgroup.vms.security.JwtService;
import com.starkgroup.vms.user.User;
import com.starkgroup.vms.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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
        String token = jwtService.generateToken(user.getUsername());
        return new LoginResponse(token, jwtService.expirationInstant(), UserResponse.from(user));
    }
}
