package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.dto.CharacterAssetResponse;
import p5laris.character.domain.application.dto.CharacterTypeResponse;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterTypeRepository characterTypeRepository;
    private final CharacterAssetRepository characterAssetRepository;
    private final p5laris.character.domain.domain.repository.UserCharacterRepository userCharacterRepository;

    /**
     * Get active character types ordered by sort_order ascending.
     * API spec 4.1 GET /api/character/v1/character-types
     */
    @Transactional(readOnly = true)
    public List<CharacterTypeResponse> getCharacterTypes() {
        return characterTypeRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(ct -> CharacterTypeResponse.builder()
                        .id(ct.getId())
                        .code(ct.getCode())
                        .name(ct.getName())
                        .summary(ct.getSummary())
                        .sampleLine(ct.getSampleLine())
                        .sortOrder(ct.getSortOrder())
                        .build())
                .toList();
    }

    /**
     * Get all assets for a character type.
     * API spec 4.2 GET /api/character/v1/character-types/{characterTypeId}/assets
     *
     * Throws IllegalArgumentException if the character type does not exist.
     */
    @Transactional(readOnly = true)
    public List<CharacterAssetResponse> getCharacterAssets(Long characterTypeId) {
        if (!characterTypeRepository.existsById(characterTypeId)) {
            throw new IllegalArgumentException("CharacterType not found: " + characterTypeId);
        }
        return characterAssetRepository.findByCharacterTypeId(characterTypeId)
                .stream()
                .map(a -> CharacterAssetResponse.builder()
                        .assetType(a.getAssetType())
                        .assetUrl(a.getAssetUrl())
                        .build())
                .toList();
    }

    /**
     * Create a user character.
     * API spec 4.3 POST /api/character/v1/characters
     */
    @Transactional
    public p5laris.character.domain.application.dto.UserCharacterResponse createCharacter(Long userId, Long characterTypeId, String name) {
        p5laris.character.domain.domain.entity.CharacterType characterType = characterTypeRepository.findById(characterTypeId)
                .orElseThrow(() -> new IllegalArgumentException("CharacterType not found: " + characterTypeId));

        // Deactivate existing active character if any
        userCharacterRepository.findByUserIdAndActiveTrue(userId)
                .ifPresent(p5laris.character.domain.domain.entity.UserCharacter::deactivate);

        p5laris.character.domain.domain.entity.UserCharacter newCharacter = p5laris.character.domain.domain.entity.UserCharacter.builder()
                .userId(userId)
                .characterType(characterType)
                .name(name)
                .level(1)
                .exp(0)
                .fullness(70)
                .energy(70)
                .affection(50)
                .active(true)
                .build();

        userCharacterRepository.save(newCharacter);

        return p5laris.character.domain.application.dto.UserCharacterResponse.builder()
                .id(newCharacter.getId())
                .name(newCharacter.getName())
                .characterTypeCode(characterType.getCode())
                .active(newCharacter.isActive())
                .states(p5laris.character.domain.application.dto.UserCharacterResponse.States.builder()
                        .hunger(newCharacter.getFullness())
                        .energy(newCharacter.getEnergy())
                        .affection(newCharacter.getAffection())
                        .build())
                .createdAt(newCharacter.getCreatedAt())
                .build();
    }
}
