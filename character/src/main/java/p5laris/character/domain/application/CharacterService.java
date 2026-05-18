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
}
