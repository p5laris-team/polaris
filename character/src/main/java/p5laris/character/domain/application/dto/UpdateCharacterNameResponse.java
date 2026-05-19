package p5laris.character.domain.application.dto;

import lombok.Builder;
import java.time.Instant;

/**
 * DTO for UpdateCharacterName API spec 4.5.
 */
@Builder
public record UpdateCharacterNameResponse(
        Long id,
        String name,
        Instant updatedAt
) {}
