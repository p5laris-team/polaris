package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * Response DTO for PerformCareAction (API spec 4.7).
 */
@Builder
public record CareActionResponse(
        Long careLogId,
        Long characterId,
        String actionType,
        Long consumedItemId,     // null if no item used
        int consumedQuantity,    // 0 if no item used
        States beforeStates,
        States afterStates,
        String characterMessage
) {
    @Builder
    public record States(
            int hunger,
            int energy,
            int affection
    ) {}
}
