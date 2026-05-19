package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.dto.CharacterAssetResponse;
import p5laris.character.domain.application.dto.CharacterTypeResponse;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterCareLogRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterTypeRepository characterTypeRepository;
    private final CharacterAssetRepository characterAssetRepository;
    private final p5laris.character.domain.domain.repository.UserCharacterRepository userCharacterRepository;
    private final CharacterCareLogRepository characterCareLogRepository;

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

    /**
     * Get user's active character.
     * API spec 4.4 GET /api/character/v1/characters/me
     *
     * Returns null or throws exception if not found? Let's throw an exception for simplicity if the user has no active character.
     * Or return Optional. We will throw an exception since the API spec doesn't specify a null response.
     */
    @Transactional
    public p5laris.character.domain.application.dto.MyCharacterResponse getMyCharacter(Long userId) {
        p5laris.character.domain.domain.entity.UserCharacter userCharacter = userCharacterRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new IllegalArgumentException("Active character not found for user: " + userId));

        // 지연 평가: 상태 감소 로직 실행 (JPA Dirty checking으로 DB 반영됨)
        userCharacter.calculateTimeBasedStatDecrease();

        return p5laris.character.domain.application.dto.MyCharacterResponse.builder()
                .id(userCharacter.getId())
                .name(userCharacter.getName())
                .characterTypeCode(userCharacter.getCharacterType().getCode())
                .active(userCharacter.isActive())
                .equippedSkinId(userCharacter.getEquippedSkinId() != null ? userCharacter.getEquippedSkinId() : 0L)
                .build();
    }

    /**
     * Update character name.
     * API spec 4.5 PATCH /api/character/v1/characters/{characterId}
     */
    @Transactional
    public p5laris.character.domain.application.dto.UpdateCharacterNameResponse updateCharacterName(Long characterId, Long userId, String newName) {
        p5laris.character.domain.domain.entity.UserCharacter userCharacter = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));

        if (!userCharacter.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own this character");
        }

        userCharacter.updateName(newName);

        return p5laris.character.domain.application.dto.UpdateCharacterNameResponse.builder()
                .id(userCharacter.getId())
                .name(userCharacter.getName())
                .updatedAt(userCharacter.getUpdatedAt())
                .build();
    }

    /**
     * Get character status (API spec 4.6).
     */
    @Transactional
    public p5laris.character.domain.application.dto.CharacterStatusResponse getCharacterStatus(Long characterId, Long userId) {
        p5laris.character.domain.domain.entity.UserCharacter userCharacter = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));

        if (!userCharacter.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own this character");
        }

        // 지연 평가: 상태 감소 로직 실행
        userCharacter.calculateTimeBasedStatDecrease();

        return p5laris.character.domain.application.dto.CharacterStatusResponse.builder()
                .characterId(userCharacter.getId())
                .states(p5laris.character.domain.application.dto.CharacterStatusResponse.States.builder()
                        .hunger(buildStateDetail(userCharacter.getFullness(), "든든함", "적당함", "배고픔"))
                        .energy(buildStateDetail(userCharacter.getEnergy(), "활기참", "졸림", "지침"))
                        .affection(buildStateDetail(userCharacter.getAffection(), "행복함", "평온함", "쓸쓸함"))
                        .build())
                .build();
    }

    private p5laris.character.domain.application.dto.CharacterStatusResponse.StateDetail buildStateDetail(int value, String goodLabel, String normalLabel, String badLabel) {
        String grade;
        String label;

        if (value >= 70) {
            grade = "GOOD";
            label = goodLabel;
        } else if (value >= 30) {
            grade = "NORMAL";
            label = normalLabel;
        } else {
            grade = "BAD";
            label = badLabel;
        }

        return p5laris.character.domain.application.dto.CharacterStatusResponse.StateDetail.builder()
                .value(value)
                .label(label)
                .grade(grade)
                .build();
    }

    /**
     * Perform a care action on a character.
     * API spec 4.7 POST /api/character/v1/characters/{characterId}/care-logs
     *
     * Business rules (AGENTS.md §20.2):
     * - FEED  → fullness  +30 (max 100)
     * - SLEEP → energy    +30 (max 100)
     * - PLAY  → affection +30 (max 100)
     *
     * Item consumption:
     * TODO [Item Domain Integration]: if itemId > 0, call item domain to deduct 1 quantity.
     * Currently skipped; itemId is stored in care log but no deduction is made.
     */
    @Transactional
    public p5laris.character.domain.application.dto.CareActionResponse performCareAction(
            Long characterId, Long userId, String actionTypeStr, Long itemId) {

        // 1. Ownership validation
        p5laris.character.domain.domain.entity.UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));

        if (!character.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own this character");
        }

        // 2. Parse action type
        ActionType actionType;
        try {
            actionType = ActionType.valueOf(actionTypeStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid action type: " + actionTypeStr);
        }

        // 3. Capture before state
        p5laris.character.domain.application.dto.CareActionResponse.States beforeStates =
                p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                        .hunger(character.getFullness())
                        .energy(character.getEnergy())
                        .affection(character.getAffection())
                        .build();

        // 4. Apply care (fixed +30 per action, MVP)
        final int CARE_AMOUNT = 30;
        character.applyCare(actionType, CARE_AMOUNT);

        // 5. Capture after state
        p5laris.character.domain.application.dto.CareActionResponse.States afterStates =
                p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                        .hunger(character.getFullness())
                        .energy(character.getEnergy())
                        .affection(character.getAffection())
                        .build();

        // 6. TODO [Item Domain Integration]: if itemId > 0, call item service to deduct quantity.
        //    Example (uncomment after item domain is ready):
        //    if (itemId != null && itemId > 0) {
        //        itemService.deductUserItem(userId, itemId, 1);
        //    }
        long resolvedItemId = (itemId != null) ? itemId : 0L;

        // 7. Build state snapshot JSON (simple format)
        String beforeJson = String.format(
                "{\"fullness\":%d,\"energy\":%d,\"affection\":%d}",
                beforeStates.hunger(), beforeStates.energy(), beforeStates.affection());
        String afterJson = String.format(
                "{\"fullness\":%d,\"energy\":%d,\"affection\":%d}",
                afterStates.hunger(), afterStates.energy(), afterStates.affection());

        // 8. Persist care log
        CharacterCareLog careLog = CharacterCareLog.builder()
                .userId(userId)
                .characterId(characterId)
                .itemId(resolvedItemId > 0 ? resolvedItemId : null)
                .actionType(actionType)
                .beforeStateJson(beforeJson)
                .afterStateJson(afterJson)
                .build();
        characterCareLogRepository.save(careLog);

        // 9. Build character message (MVP fixed messages per action type)
        String characterMessage = switch (actionType) {
            case FEED  -> "Mmm... light has a taste too.";
            case SLEEP -> "...zz. Thanks.";
            case PLAY  -> "That was fun. Let's do it again sometime.";
        };

        return p5laris.character.domain.application.dto.CareActionResponse.builder()
                .careLogId(careLog.getId())
                .characterId(characterId)
                .actionType(actionType.name())
                .consumedItemId(resolvedItemId > 0 ? resolvedItemId : null)
                .consumedQuantity(resolvedItemId > 0 ? 1 : 0)
                .beforeStates(beforeStates)
                .afterStates(afterStates)
                .characterMessage(characterMessage)
                .build();
    }

    /**
     * Equip a skin item on a character.
     * API spec 4.8 PUT /api/character/v1/characters/{characterId}/equipped-skin
     *
     * Item ownership validation:
     * TODO [Item Domain Integration]: verify that userId actually owns itemId before equipping.
     * Currently skipped; the item is applied directly to the character.
     */
    @Transactional
    public p5laris.character.domain.application.dto.EquipSkinResponse equipSkin(
            Long characterId, Long userId, Long itemId) {

        // 1. Load character and validate ownership
        p5laris.character.domain.domain.entity.UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new IllegalArgumentException("Character not found: " + characterId));

        if (!character.getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not own this character");
        }

        // 2. TODO [Item Domain Integration]: verify user owns the skin item.
        //    Example (uncomment after item domain is ready):
        //    if (!itemService.userOwnsItem(userId, itemId)) {
        //        throw new IllegalArgumentException("User does not own item: " + itemId);
        //    }

        // 3. Equip the skin
        character.equipSkin(itemId);

        return p5laris.character.domain.application.dto.EquipSkinResponse.builder()
                .characterId(characterId)
                .equippedSkinId(itemId)
                .updatedAt(character.getUpdatedAt())
                .build();
    }
}
