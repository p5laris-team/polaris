package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.p5laris.proto.item.v1.GetUserItemsRequest;
import com.p5laris.proto.item.v1.ItemServiceGrpc;
import com.p5laris.proto.item.v1.UseItemRequest;
import com.p5laris.proto.item.v1.UserItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.dto.CharacterAssetResponse;
import p5laris.character.domain.application.dto.CharacterTypeResponse;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.enums.StatGrade;
import p5laris.character.domain.domain.enums.StatType;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterCareLogRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;

import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private static final int CARE_AMOUNT = 30;

    private final CharacterTypeRepository characterTypeRepository;
    private final CharacterAssetRepository characterAssetRepository;
    private final UserCharacterRepository userCharacterRepository;
    private final CharacterCareLogRepository characterCareLogRepository;
    private final ObjectMapper objectMapper;

    @GrpcClient("item")
    private ItemServiceGrpc.ItemServiceBlockingStub itemStub;

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

    @Transactional(readOnly = true)
    public List<CharacterAssetResponse> getCharacterAssets(Long characterTypeId) {
        if (!characterTypeRepository.existsById(characterTypeId)) {
            throw new CharacterException(CharacterErrorCode.CHARACTER_TYPE_NOT_FOUND);
        }
        return characterAssetRepository.findByCharacterTypeId(characterTypeId)
                .stream()
                .map(a -> CharacterAssetResponse.builder()
                        .assetType(a.getAssetType())
                        .assetUrl(a.getAssetUrl())
                        .build())
                .toList();
    }

    @Transactional
    public p5laris.character.domain.application.dto.UserCharacterResponse createCharacter(
            Long userId, Long characterTypeId, String name) {
        CharacterType characterType = characterTypeRepository.findById(characterTypeId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_TYPE_NOT_FOUND));

        if (name == null || name.trim().isEmpty() || name.length() > 10) {
            throw new CharacterException(CharacterErrorCode.INVALID_CHARACTER_NAME);
        }

        userCharacterRepository.findByUserIdAndActiveTrue(userId)
                .ifPresent(c -> {
                    c.deactivate();
                    userCharacterRepository.saveAndFlush(c);
                });

        UserCharacter newCharacter = UserCharacter.builder()
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

    @Transactional
    public p5laris.character.domain.application.dto.MyCharacterResponse getMyCharacter(Long userId) {
        UserCharacter userCharacter = userCharacterRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        userCharacter.calculateTimeBasedStatDecrease();

        Map<String, String> assetUrls = buildAssetUrls(userCharacter);
        String currentAssetUrl = assetUrls.getOrDefault(userCharacter.calculateMood().responseKey(), "");

        return p5laris.character.domain.application.dto.MyCharacterResponse.builder()
                .id(userCharacter.getId())
                .name(userCharacter.getName())
                .characterTypeCode(userCharacter.getCharacterType().getCode())
                .active(userCharacter.isActive())
                .equippedSkinId(userCharacter.getEquippedSkinId() != null ? userCharacter.getEquippedSkinId() : 0L)
                .states(p5laris.character.domain.application.dto.MyCharacterResponse.States.builder()
                        .hunger(userCharacter.getFullness())
                        .energy(userCharacter.getEnergy())
                        .affection(userCharacter.getAffection())
                        .build())
                .currentAssetUrl(currentAssetUrl)
                .build();
    }

    private Map<String, String> buildAssetUrls(UserCharacter userCharacter) {
        Long characterTypeId = userCharacter.getCharacterType().getId();
        Map<String, String> assetUrls = buildCharacterAssetUrls(characterTypeId);
        Long equippedSkinId = userCharacter.getEquippedSkinId();
        if (equippedSkinId == null || equippedSkinId <= 0) {
            return assetUrls;
        }

        try {
            com.p5laris.proto.item.v1.GetSkinAssetsResponse response = itemStub.getSkinAssets(
                    com.p5laris.proto.item.v1.GetSkinAssetsRequest.newBuilder()
                            .setSkinItemId(equippedSkinId)
                            .setCharacterTypeId(characterTypeId)
                            .build()
            );
            Map<String, String> skinAssetUrls = response.getAssetUrlsMap();
            if (skinAssetUrls != null && !skinAssetUrls.isEmpty()) {
                assetUrls.putAll(skinAssetUrls);
            }
        } catch (Exception e) {
            log.error("Failed to get skin assets from item service for skinItemId: {}, characterTypeId: {}", 
                    equippedSkinId, characterTypeId, e);
        }
        return assetUrls;
    }

    private Map<String, String> buildCharacterAssetUrls(Long characterTypeId) {
        Map<String, String> assetUrls = new LinkedHashMap<>();
        characterAssetRepository.findByCharacterTypeId(characterTypeId)
                .forEach(asset -> {
                    putMoodAsset(assetUrls, asset.getAssetType(), asset.getAssetUrl());
                });
        return assetUrls;
    }

    private void putMoodAsset(Map<String, String> assetUrls, String assetType, String assetUrl) {
        try {
            CharacterMood mood = CharacterMood.fromAssetType(assetType);
            assetUrls.put(mood.responseKey(), assetUrl);
        } catch (IllegalArgumentException e) {
            log.debug("Skipping unknown character asset type: {}", assetType);
        }
    }

    @Transactional
    public p5laris.character.domain.application.dto.UpdateCharacterNameResponse updateCharacterName(
            Long characterId, Long userId, String newName) {
        UserCharacter userCharacter = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!userCharacter.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        userCharacter.updateName(newName);

        return p5laris.character.domain.application.dto.UpdateCharacterNameResponse.builder()
                .id(userCharacter.getId())
                .name(userCharacter.getName())
                .updatedAt(userCharacter.getUpdatedAt())
                .build();
    }

    @Transactional
    public p5laris.character.domain.application.dto.CharacterStatusResponse getCharacterStatus(
            Long characterId, Long userId) {
        UserCharacter userCharacter = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!userCharacter.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        userCharacter.calculateTimeBasedStatDecrease();

        return p5laris.character.domain.application.dto.CharacterStatusResponse.builder()
                .characterId(userCharacter.getId())
                .states(p5laris.character.domain.application.dto.CharacterStatusResponse.States.builder()
                        .hunger(buildStateDetail(userCharacter.getFullness(), StatType.FULLNESS))
                        .energy(buildStateDetail(userCharacter.getEnergy(), StatType.ENERGY))
                        .affection(buildStateDetail(userCharacter.getAffection(), StatType.AFFECTION))
                        .build())
                .build();
    }

    private p5laris.character.domain.application.dto.CharacterStatusResponse.StateDetail buildStateDetail(
            int value, StatType statType) {
        StatGrade grade = StatGrade.fromValue(value);

        return p5laris.character.domain.application.dto.CharacterStatusResponse.StateDetail.builder()
                .value(value)
                .label(statType.getLabel(grade))
                .grade(grade.name())
                .build();
    }

    @Transactional
    public p5laris.character.domain.application.dto.CareActionResponse performCareAction(
            Long characterId, Long userId, String actionTypeStr, Long itemId, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        var existingLog = characterCareLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existingLog.isPresent()) {
            return replayCareLog(existingLog.get());
        }

        UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!character.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        ActionType actionType = parseActionType(actionTypeStr);
        long resolvedItemId = requirePositiveItemId(itemId);
        UserItem careItem = findOwnedItem(userId, resolvedItemId, "CONSUMABLE");
        validateCareItemMatchesAction(careItem, actionType);

        var beforeStates = p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                .hunger(character.getFullness())
                .energy(character.getEnergy())
                .affection(character.getAffection())
                .build();

        character.applyCare(actionType, CARE_AMOUNT);

        var afterStates = p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                .hunger(character.getFullness())
                .energy(character.getEnergy())
                .affection(character.getAffection())
                .build();

        CharacterCareLog careLog = CharacterCareLog.builder()
                .userId(userId)
                .characterId(characterId)
                .itemId(resolvedItemId)
                .actionType(actionType)
                .beforeStateJson(toStateJson(beforeStates))
                .afterStateJson(toStateJson(afterStates))
                .idempotencyKey(idempotencyKey)
                .build();
        CharacterCareLog savedCareLog = characterCareLogRepository.save(careLog);

        try {
            itemStub.useItem(
                    UseItemRequest.newBuilder()
                            .setUserId(userId)
                            .setItemId(resolvedItemId)
                            .setQuantity(1)
                            .setRefType("CARE_ACTION")
                            .setRefId(savedCareLog.getId() != null ? savedCareLog.getId() : 0L)
                            .setIdempotencyKey(idempotencyKey)
                            .build()
            );
        } catch (Exception e) {
            log.warn("[CharacterService] UseItem gRPC failed: userId={}, itemId={}, careLogId={}, msg={}",
                    userId, resolvedItemId, careLog.getId(), e.getMessage());
            throw mapItemFailure(e);
        }

        return p5laris.character.domain.application.dto.CareActionResponse.builder()
                .careLogId(careLog.getId())
                .characterId(characterId)
                .actionType(actionType.name())
                .consumedItemId(resolvedItemId)
                .consumedQuantity(1)
                .beforeStates(beforeStates)
                .afterStates(afterStates)
                .characterMessage(characterMessage(actionType))
                .build();
    }

    @Transactional
    public p5laris.character.domain.application.dto.EquipSkinResponse equipSkin(
            Long characterId, Long userId, Long itemId) {

        UserCharacter character = userCharacterRepository.findById(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!character.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        // 2. verify user owns the skin item.
        if (itemId != null && itemId > 0) {
            try {
                findOwnedItem(userId, itemId, "SKIN");
            } catch (CharacterException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to verify skin ownership for userId: {}, itemId: {}", userId, itemId, e);
                throw new CharacterException(CharacterErrorCode.ITEM_SERVICE_CALL_FAILED);
            }
        }

        Long equippedSkinId = itemId;
        if (itemId == null || itemId <= 0) {
            character.unequipSkin();
            equippedSkinId = null;
        } else {
            character.equipSkin(itemId);
        }

        return p5laris.character.domain.application.dto.EquipSkinResponse.builder()
                .characterId(characterId)
                .equippedSkinId(equippedSkinId)
                .updatedAt(character.getUpdatedAt())
                .build();
    }

    private p5laris.character.domain.application.dto.CareActionResponse replayCareLog(CharacterCareLog log) {
        try {
            var beforeNode = objectMapper.readTree(log.getBeforeStateJson());
            var afterNode = objectMapper.readTree(log.getAfterStateJson());

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
                    .characterMessage(characterMessage(log.getActionType()))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cached care log state", e);
        }
    }

    private ActionType parseActionType(String actionTypeStr) {
        try {
            return ActionType.valueOf(actionTypeStr);
        } catch (IllegalArgumentException e) {
            throw new CharacterException(CharacterErrorCode.INVALID_ACTION_TYPE);
        }
    }

    private long requirePositiveItemId(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new CharacterException(CharacterErrorCode.INVALID_CARE_ITEM);
        }
        return itemId;
    }

    private UserItem findOwnedItem(Long userId, Long itemId, String itemType) {
        String cursor = "";
        int guard = 0;
        do {
            var response = itemStub.getUserItems(
                    GetUserItemsRequest.newBuilder()
                            .setUserId(userId)
                            .setItemType(itemType)
                            .setCursor(cursor)
                            .setSize(100)
                            .build()
            );

            for (UserItem item : response.getItemsList()) {
                if (item.getItemId() == itemId) {
                    return item;
                }
            }

            cursor = response.hasPageInfo() ? response.getPageInfo().getNextCursor() : "";
            guard++;
        } while (cursor != null && !cursor.isBlank() && guard < 20);

        throw new CharacterException(CharacterErrorCode.ITEM_NOT_OWNED);
    }

    private void validateCareItemMatchesAction(UserItem item, ActionType actionType) {
        String requiredEffectType = switch (actionType) {
            case FEED -> "FOOD";
            case SLEEP -> "REST";
            case PLAY -> "PLAY";
        };

        if (!"CONSUMABLE".equals(item.getItemType())
                || !requiredEffectType.equals(item.getEffectType())
                || item.getQuantity() <= 0) {
            throw new CharacterException(CharacterErrorCode.INVALID_CARE_ITEM);
        }
    }

    private CharacterException mapItemFailure(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("ITEM_QUANTITY_NOT_ENOUGH") || message.contains("ITEM-006")) {
            return new CharacterException(CharacterErrorCode.ITEM_QUANTITY_NOT_ENOUGH);
        }
        if (message.contains("USER_ITEM_NOT_FOUND") || message.contains("ITEM_NOT_FOUND") || message.contains("ITEM-005")) {
            return new CharacterException(CharacterErrorCode.ITEM_NOT_OWNED);
        }
        return new CharacterException(CharacterErrorCode.INVALID_CARE_ITEM);
    }

    private String toStateJson(p5laris.character.domain.application.dto.CareActionResponse.States states) {
        return String.format(
                "{\"fullness\":%d,\"energy\":%d,\"affection\":%d}",
                states.hunger(), states.energy(), states.affection()
        );
    }

    private String characterMessage(ActionType actionType) {
        return switch (actionType) {
            case FEED -> "먹는 중... 빛도 맛이 있구나.";
            case SLEEP -> "...zz. 고마워요.";
            case PLAY -> "재밌었어요. 다음에 또 놀아요.";
        };
    }
}
