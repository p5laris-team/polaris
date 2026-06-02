package p5laris.character.domain.application.dto;

import lombok.Builder;

import java.util.Map;


/**
 * DTO for GetMyCharacter API spec 4.4.
 */
@Builder
public record MyCharacterResponse(
        Long id,
        String name,
        String characterTypeCode,
        boolean active,
        Long equippedSkinId,
        States states,
        CharacterGrowthResponse growth,
        String currentAssetUrl,
        Map<String, String> assetUrls
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
