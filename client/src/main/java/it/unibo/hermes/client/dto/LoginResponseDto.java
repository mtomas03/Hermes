package it.unibo.hermes.client.dto;

import java.time.Instant;

public record LoginResponseDto(
        String token,
        String username,
        Instant expiresAt) {
}
