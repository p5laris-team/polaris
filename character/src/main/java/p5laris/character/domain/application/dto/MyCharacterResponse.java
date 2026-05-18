package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * DTO for GetMyCharacter API spec 4.4.
 */
@Builder
public record MyCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        boolean active,
        Long equippedSkinId
) {}
