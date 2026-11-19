package com.starkgroup.vms.auth;

import java.time.Instant;

public record LoginResponse(
        String token,
        Instant expiresAt,
        UserResponse user
) {
}
