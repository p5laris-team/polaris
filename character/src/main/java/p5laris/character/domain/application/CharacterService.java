package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.dto.CharacterAssetResponse;
import p5laris.character.domain.application.dto.CharacterTypeResponse;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterCareLogRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;
import com.p5laris.proto.item.v1.ItemServiceGrpc;
import com.p5laris.proto.item.v1.UseItemRequest;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterTypeRepository characterTypeRepository;
    private final CharacterAssetRepository characterAssetRepository;
    private final p5laris.character.domain.domain.repository.UserCharacterRepository userCharacterRepository;
    private final CharacterCareLogRepository characterCareLogRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @GrpcClient("item")
    private ItemServiceGrpc.ItemServiceBlockingStub itemStub;

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
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_TYPE_NOT_FOUND);
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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_TYPE_NOT_FOUND));

        if (name == null || name.trim().isEmpty() || name.length() > 10) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.INVALID_CHARACTER_NAME);
        }

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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!userCharacter.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_CHARACTER_OWNER);
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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!userCharacter.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        // 지연 평가: 상태 감소 로직 실행
        userCharacter.calculateTimeBasedStatDecrease();

        return p5laris.character.domain.application.dto.CharacterStatusResponse.builder()
                .characterId(userCharacter.getId())
                .states(p5laris.character.domain.application.dto.CharacterStatusResponse.States.builder()
                        .hunger(buildStateDetail(userCharacter.getFullness(), p5laris.character.domain.domain.enums.StatType.FULLNESS))
                        .energy(buildStateDetail(userCharacter.getEnergy(), p5laris.character.domain.domain.enums.StatType.ENERGY))
                        .affection(buildStateDetail(userCharacter.getAffection(), p5laris.character.domain.domain.enums.StatType.AFFECTION))
                        .build())
                .build();
    }

    private p5laris.character.domain.application.dto.CharacterStatusResponse.StateDetail buildStateDetail(int value, p5laris.character.domain.domain.enums.StatType statType) {
        p5laris.character.domain.domain.enums.StatGrade grade = p5laris.character.domain.domain.enums.StatGrade.fromValue(value);

        return p5laris.character.domain.application.dto.CharacterStatusResponse.StateDetail.builder()
                .value(value)
                .label(statType.getLabel(grade))
                .grade(grade.name())
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
            Long characterId, Long userId, String actionTypeStr, Long itemId, String idempotencyKey) {

        // 0. Idempotency Check & Validation
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        java.util.Optional<CharacterCareLog> existingLog = characterCareLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existingLog.isPresent()) {
            CharacterCareLog log = existingLog.get();
                try {
                    com.fasterxml.jackson.databind.JsonNode beforeNode = objectMapper.readTree(log.getBeforeStateJson());
                    com.fasterxml.jackson.databind.JsonNode afterNode = objectMapper.readTree(log.getAfterStateJson());

                    String characterMessage = switch (log.getActionType()) {
                        case FEED  -> "음… 오늘의 빛은 좀 달콤하네요.";
                        case SLEEP -> "…쿨… 고마워요.";
                        case PLAY  -> "재밌었어요. 다음에 또 놀아요.";
                    };

                    return p5laris.character.domain.application.dto.CareActionResponse.builder()
                            .careLogId(log.getId())
                            .characterId(log.getCharacterId())
                            .actionType(log.getActionType().name())
                            .consumedItemId(log.getItemId())
                            .consumedQuantity(log.getItemId() != null ? 1 : 0)
                            .beforeStates(p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                                    .hunger(beforeNode.get("fullness").asInt())
                                    .energy(beforeNode.get("energy").asInt())
                                    .affection(beforeNode.get("affection").asInt())
                                    .build())
                            .afterStates(p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                                    .hunger(afterNode.get("fullness").asInt())
                                    .energy(afterNode.get("energy").asInt())
                                    .affection(afterNode.get("affection").asInt())
                                    .build())
                            .characterMessage(characterMessage)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse cached care log state", e);
                }
            }

        // 1. Ownership validation
        p5laris.character.domain.domain.entity.UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!character.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        // 2. Parse action type
        ActionType actionType;
        try {
            actionType = ActionType.valueOf(actionTypeStr);
        } catch (IllegalArgumentException e) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.INVALID_ACTION_TYPE);
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
                .idempotencyKey(idempotencyKey)
                .build();
        characterCareLogRepository.save(careLog);

        // 9. [Item Domain Integration]: itemId > 0 이면 아이템 1개 소모
        if (resolvedItemId > 0) {
            try {
                itemStub.useItem(
                        UseItemRequest.newBuilder()
                                .setUserId(userId)
                                .setItemId(resolvedItemId)
                                .setQuantity(1)
                                .setRefType("CARE_ACTION")
                                .setRefId(careLog.getId())
                                .setIdempotencyKey(idempotencyKey)
                                .build()
                );
            } catch (Exception e) {
                // 아이템 소모 실패 시 돌봄 결과는 유지하고 로그만 남긴다.
                // (수량 부족, 미보유 등은 gRPC error description 으로 전달됨)
                log.error("[CharacterService] UseItem gRPC failed: userId={}, itemId={}, careLogId={}, msg={}",
                        userId, resolvedItemId, careLog.getId(), e.getMessage());
            }
        }
        String characterMessage = switch (actionType) {
            case FEED  -> "음… 오늘의 빛은 좀 달콤하네요.";
            case SLEEP -> "…쿨… 고마워요.";
            case PLAY  -> "재밌었어요. 다음에 또 놀아요.";
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
                .orElseThrow(() -> new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!character.getUserId().equals(userId)) {
            throw new p5laris.character.domain.exception.CharacterException(p5laris.character.domain.exception.CharacterErrorCode.NOT_CHARACTER_OWNER);
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
