package p5laris.character.domain.application.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO for EquipSkin (API spec 4.8).
 */
@Builder
public record EquipSkinResponse(
        Long characterId,
        Long equippedSkinId,
        Instant updatedAt
) {}
