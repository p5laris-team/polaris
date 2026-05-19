package p5laris.character.domain.application.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * User character response DTO for API spec 4.3.
 */
@Builder
public record UserCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        boolean active,
        States states,
        Instant createdAt
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
