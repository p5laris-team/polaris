package p5laris.character.domain.application.dto;

import lombok.Builder;

/**
 * DTO for GetCharacterStatus API spec 4.6.
 */
@Builder
public record CharacterStatusResponse(
        Long characterId,
        States states
) {
    @Builder
    public record States(
            StateDetail hunger,
            StateDetail energy,
            StateDetail affection
    ) {}

    @Builder
    public record StateDetail(
            int value,
            String label,
            String grade
    ) {}
}
