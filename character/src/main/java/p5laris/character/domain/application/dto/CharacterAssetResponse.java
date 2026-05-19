package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * Character asset item DTO for API spec 4.2.
 */
@Builder
public record CharacterAssetResponse(
        String assetType,
        String assetUrl
) {
}
