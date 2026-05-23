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
        String currentAssetUrl,
        boolean active,
        States states,
        EquippedSkin equippedSkin
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}

    @Builder
    public record EquippedSkin(
            Long itemId,
            String name
    ) {}
}
