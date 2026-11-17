package com.tririvenord.auth.dto;

import com.tririvenord.common.Role;

public record AuthResponse(String token, Long userId, String email, Role role) {
}
