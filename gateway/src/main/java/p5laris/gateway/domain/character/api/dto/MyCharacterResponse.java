package p5laris.gateway.domain.character.api.dto;

import lombok.Builder;

/**
 * Gateway response DTO for API spec 4.4.
 */
@Builder
public record MyCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        boolean active,
        EquippedSkin equippedSkin
) {
    @Builder
    public record EquippedSkin(
            Long itemId,
            String name
    ) {}
}
