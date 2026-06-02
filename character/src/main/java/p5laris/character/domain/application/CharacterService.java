package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.p5laris.proto.item.v1.GetUserItemsRequest;
import com.p5laris.proto.item.v1.ItemServiceGrpc;
import com.p5laris.proto.item.v1.UseItemRequest;
import com.p5laris.proto.item.v1.UserItem;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.dto.CharacterAssetResponse;
import p5laris.character.domain.application.dto.CharacterTypeResponse;
import p5laris.character.domain.application.dto.GrantCharacterExpResponse;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.entity.CharacterExpLog;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.enums.CharacterExpSourceType;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.enums.StatGrade;
import p5laris.character.domain.domain.enums.StatType;
import p5laris.character.domain.domain.policy.CharacterCareGrowthPolicy;
import p5laris.character.domain.domain.policy.CharacterGrowthPolicy;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterCareLogRepository;
import p5laris.character.domain.domain.repository.CharacterExpLogRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;

import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.config.CharacterItemGrpcProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import p5laris.character.domain.application.event.CharacterEventLogEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private static final int CARE_AMOUNT = 30;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CharacterTypeRepository characterTypeRepository;
    private final CharacterAssetRepository characterAssetRepository;
    private final UserCharacterRepository userCharacterRepository;
    private final CharacterCareLogRepository characterCareLogRepository;
    private final CharacterExpLogRepository characterExpLogRepository;
    private final S3StorageService s3StorageService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CharacterItemGrpcProperties itemGrpcProperties;

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
                        .assetUrl(s3StorageService.toPublicUrl(a.getAssetUrl()))
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
        
        eventPublisher.publishEvent(CharacterEventLogEvent.characterCreated(newCharacter));

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
                .growth(buildGrowth(newCharacter))
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
                .growth(buildGrowth(userCharacter))
                .currentAssetUrl(currentAssetUrl)
                .assetUrls(assetUrls)
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
            com.p5laris.proto.item.v1.GetSkinAssetsResponse response = deadlineItemStub().getSkinAssets(
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
            log.error("아이템 서비스에서 스킨 에셋을 조회하지 못했습니다. skinItemId={}, characterTypeId={}",
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
            assetUrls.put(mood.responseKey(), s3StorageService.toPublicUrl(assetUrl));
        } catch (IllegalArgumentException e) {
            log.debug("알 수 없는 캐릭터 에셋 타입은 응답에서 제외합니다. assetType={}", assetType);
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
                .growth(buildGrowth(userCharacter))
                .build();
    }

    private p5laris.character.domain.application.dto.CharacterGrowthResponse buildGrowth(UserCharacter userCharacter) {
        var growth = CharacterGrowthPolicy.calculate(userCharacter.getExp());
        return toGrowthResponse(growth);
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
            throw new CharacterException(CharacterErrorCode.INVALID_IDEMPOTENCY_KEY);
        }

        var existingLog = characterCareLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existingLog.isPresent()) {
            return replayCareLog(existingLog.get());
        }

        ActionType actionType = parseActionType(actionTypeStr);
        long resolvedItemId = requirePositiveItemId(itemId);
        UserItem careItem = findOwnedItem(userId, resolvedItemId, "CONSUMABLE");
        validateCareItemMatchesAction(careItem, actionType);

        UserCharacter character = userCharacterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));

        if (!character.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        var beforeStates = p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                .hunger(character.getFullness())
                .energy(character.getEnergy())
                .affection(character.getAffection())
                .build();
        var beforeGrowth = buildGrowth(character);
        int earnedCountToday = countTodayEarnedCareExp(characterId, actionType);
        int expGained = CharacterCareGrowthPolicy.calculateExpGained(actionType, earnedCountToday);

        character.applyCare(actionType, CARE_AMOUNT);
        if (expGained > 0) {
            character.gainExp(expGained);
        }

        var afterStates = p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                .hunger(character.getFullness())
                .energy(character.getEnergy())
                .affection(character.getAffection())
                .build();
        var afterGrowth = buildGrowth(character);
        boolean levelUp = afterGrowth.level() > beforeGrowth.level();

        CharacterCareLog careLog = CharacterCareLog.builder()
                .userId(userId)
                .characterId(characterId)
                .itemId(resolvedItemId)
                .actionType(actionType)
                .beforeStateJson(toStateJson(beforeStates, beforeGrowth))
                .afterStateJson(toStateJson(afterStates, afterGrowth, expGained, levelUp))
                .idempotencyKey(idempotencyKey)
                .build();
        CharacterCareLog savedCareLog = characterCareLogRepository.save(careLog);

        try {
            deadlineItemStub().useItem(
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
            log.warn("돌보기 아이템 사용 gRPC 호출에 실패했습니다. userId={}, itemId={}, careLogId={}, statusCode={}, errorCode={}",
                    userId, resolvedItemId, savedCareLog.getId(), grpcStatusCode(e), grpcErrorCode(e), e);
            throw mapItemFailure(e);
        }

        return p5laris.character.domain.application.dto.CareActionResponse.builder()
                .careLogId(savedCareLog.getId())
                .characterId(characterId)
                .actionType(actionType.name())
                .consumedItemId(resolvedItemId)
                .consumedQuantity(1)
                .beforeStates(beforeStates)
                .afterStates(afterStates)
                .beforeGrowth(beforeGrowth)
                .afterGrowth(afterGrowth)
                .expGained(expGained)
                .levelUp(levelUp)
                .characterMessage(characterMessage(actionType))
                .build();
    }

    @Transactional
    public GrantCharacterExpResponse grantCharacterExp(
            Long userId,
            Long characterId,
            String sourceTypeValue,
            Long sourceId,
            int expAmount,
            String idempotencyKey
    ) {
        validateExpGrantRequest(sourceId, expAmount, idempotencyKey);
        CharacterExpSourceType sourceType = parseExpSourceType(sourceTypeValue);

        var existingByKey = characterExpLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return replayMatchingExpLog(existingByKey.get(), userId, characterId, sourceType, sourceId, idempotencyKey);
        }

        var existingBySource = characterExpLogRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
        if (existingBySource.isPresent()) {
            return replayMatchingSourceExpLog(existingBySource.get(), userId, characterId, sourceType, sourceId);
        }

        UserCharacter character = userCharacterRepository.findByIdForUpdate(characterId)
                .orElseThrow(() -> new CharacterException(CharacterErrorCode.CHARACTER_NOT_FOUND));
        if (!character.getUserId().equals(userId)) {
            throw new CharacterException(CharacterErrorCode.NOT_CHARACTER_OWNER);
        }

        /*
         * 같은 캐릭터 lock을 기다리던 중 먼저 성공한 요청이 있을 수 있으므로
         * lock 획득 뒤 한 번 더 로그를 확인해 최종 중복 지급을 막는다.
         */
        existingByKey = characterExpLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            return replayMatchingExpLog(existingByKey.get(), userId, characterId, sourceType, sourceId, idempotencyKey);
        }
        existingBySource = characterExpLogRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
        if (existingBySource.isPresent()) {
            return replayMatchingSourceExpLog(existingBySource.get(), userId, characterId, sourceType, sourceId);
        }

        var beforeGrowth = buildGrowth(character);
        character.gainExp(expAmount);
        var afterGrowth = buildGrowth(character);
        boolean levelUp = afterGrowth.level() > beforeGrowth.level();

        CharacterExpLog expLog = CharacterExpLog.builder()
                .userId(userId)
                .characterId(characterId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .idempotencyKey(idempotencyKey)
                .expAmount(expAmount)
                .beforeExp(beforeGrowth.exp())
                .afterExp(afterGrowth.exp())
                .beforeLevel(beforeGrowth.level())
                .afterLevel(afterGrowth.level())
                .build();
        characterExpLogRepository.save(expLog);

        return GrantCharacterExpResponse.builder()
                .characterId(characterId)
                .expGained(expAmount)
                .beforeGrowth(beforeGrowth)
                .afterGrowth(afterGrowth)
                .levelUp(levelUp)
                .alreadyProcessed(false)
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

        // 스킨 장착 전 유저가 해당 스킨 아이템을 보유했는지 확인한다.
        if (itemId != null && itemId > 0) {
            try {
                findOwnedItem(userId, itemId, "SKIN");
            } catch (CharacterException e) {
                throw e;
            } catch (Exception e) {
                log.error("스킨 보유 여부를 확인하지 못했습니다. userId={}, itemId={}", userId, itemId, e);
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
            var beforeGrowth = parseGrowth(beforeNode.get("growth"));
            var afterGrowth = parseGrowth(afterNode.get("growth"));
            int expGained = afterNode.path("expGained").asInt(0);
            boolean levelUp = afterNode.path("levelUp").asBoolean(false);

            return p5laris.character.domain.application.dto.CareActionResponse.builder()
                    .careLogId(log.getId())
                    .characterId(log.getCharacterId())
                    .actionType(log.getActionType().name())
                    .consumedItemId(log.getItemId())
                    .consumedQuantity(log.getItemId() != null ? 1 : 0)
                    .beforeStates(parseStates(beforeNode))
                    .afterStates(parseStates(afterNode))
                    .beforeGrowth(beforeGrowth)
                    .afterGrowth(afterGrowth)
                    .expGained(expGained)
                    .levelUp(levelUp)
                    .characterMessage(characterMessage(log.getActionType()))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("저장된 돌봄 로그 상태를 파싱하지 못했습니다.", e);
        }
    }

    private ActionType parseActionType(String actionTypeStr) {
        try {
            return ActionType.valueOf(actionTypeStr);
        } catch (IllegalArgumentException e) {
            throw new CharacterException(CharacterErrorCode.INVALID_ACTION_TYPE);
        }
    }

    private CharacterExpSourceType parseExpSourceType(String sourceTypeValue) {
        if (sourceTypeValue == null || sourceTypeValue.isBlank()) {
            throw new CharacterException(CharacterErrorCode.INVALID_EXP_GRANT_REQUEST);
        }
        try {
            return CharacterExpSourceType.valueOf(sourceTypeValue.trim());
        } catch (IllegalArgumentException e) {
            throw new CharacterException(CharacterErrorCode.INVALID_EXP_GRANT_REQUEST);
        }
    }

    private void validateExpGrantRequest(Long sourceId, int expAmount, String idempotencyKey) {
        if (sourceId == null || sourceId <= 0 || expAmount <= 0) {
            throw new CharacterException(CharacterErrorCode.INVALID_EXP_GRANT_REQUEST);
        }
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 120) {
            throw new CharacterException(CharacterErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
    }

    private GrantCharacterExpResponse replayMatchingExpLog(
            CharacterExpLog expLog,
            Long userId,
            Long characterId,
            CharacterExpSourceType sourceType,
            Long sourceId,
            String idempotencyKey
    ) {
        if (!expLog.matches(userId, characterId, sourceType, sourceId, idempotencyKey)) {
            throw new CharacterException(CharacterErrorCode.INVALID_EXP_GRANT_REQUEST);
        }
        return replayExpLog(expLog);
    }

    private GrantCharacterExpResponse replayMatchingSourceExpLog(
            CharacterExpLog expLog,
            Long userId,
            Long characterId,
            CharacterExpSourceType sourceType,
            Long sourceId
    ) {
        if (!expLog.matchesSource(userId, characterId, sourceType, sourceId)) {
            throw new CharacterException(CharacterErrorCode.INVALID_EXP_GRANT_REQUEST);
        }
        return replayExpLog(expLog);
    }

    private GrantCharacterExpResponse replayExpLog(CharacterExpLog expLog) {
        var beforeGrowth = toGrowthResponse(CharacterGrowthPolicy.calculate(expLog.getBeforeExp()));
        var afterGrowth = toGrowthResponse(CharacterGrowthPolicy.calculate(expLog.getAfterExp()));
        return GrantCharacterExpResponse.builder()
                .characterId(expLog.getCharacterId())
                .expGained(expLog.getExpAmount())
                .beforeGrowth(beforeGrowth)
                .afterGrowth(afterGrowth)
                .levelUp(expLog.getAfterLevel() > expLog.getBeforeLevel())
                .alreadyProcessed(true)
                .build();
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
            var response = deadlineItemStub().getUserItems(
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
        String errorCode = grpcErrorCode(e);
        if ("ITEM-006".equals(errorCode)) {
            return new CharacterException(CharacterErrorCode.ITEM_QUANTITY_NOT_ENOUGH);
        }
        if ("ITEM-001".equals(errorCode) || "ITEM-005".equals(errorCode)) {
            return new CharacterException(CharacterErrorCode.ITEM_NOT_OWNED);
        }
        return new CharacterException(CharacterErrorCode.ITEM_SERVICE_CALL_FAILED);
    }

    private ItemServiceGrpc.ItemServiceBlockingStub deadlineItemStub() {
        return itemStub.withDeadlineAfter(itemGrpcProperties.getDeadlineMs(), TimeUnit.MILLISECONDS);
    }

    private String grpcErrorCode(Exception e) {
        if (e instanceof StatusRuntimeException statusRuntimeException) {
            String description = statusRuntimeException.getStatus().getDescription();
            return description != null ? description : "";
        }
        return "";
    }

    private String grpcStatusCode(Exception e) {
        if (e instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException.getStatus().getCode().name();
        }
        return e.getClass().getSimpleName();
    }

    private int countTodayEarnedCareExp(Long characterId, ActionType actionType) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        Instant startAt = today.atStartOfDay(SERVICE_ZONE).toInstant();
        Instant endAt = today.plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant();

        List<CharacterCareLog> todayLogs = characterCareLogRepository
                .findByCharacterIdAndActionTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        characterId,
                        actionType,
                        startAt,
                        endAt
                );
        if (todayLogs == null || todayLogs.isEmpty()) {
            return 0;
        }

        return (int) todayLogs.stream()
                .filter(this::hasEarnedCareExp)
                .count();
    }

    private boolean hasEarnedCareExp(CharacterCareLog careLog) {
        try {
            JsonNode afterNode = objectMapper.readTree(careLog.getAfterStateJson());
            return afterNode.path("expGained").asInt(0) > 0;
        } catch (Exception e) {
            log.debug("돌봄 경험치 스냅샷을 읽지 못했습니다. careLogId={}", careLog.getId());
            return false;
        }
    }

    private String toStateJson(
            p5laris.character.domain.application.dto.CareActionResponse.States states,
            p5laris.character.domain.application.dto.CharacterGrowthResponse growth
    ) {
        return toStateJson(states, growth, null, null);
    }

    private String toStateJson(
            p5laris.character.domain.application.dto.CareActionResponse.States states,
            p5laris.character.domain.application.dto.CharacterGrowthResponse growth,
            Integer expGained,
            Boolean levelUp
    ) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("fullness", states.hunger());
            root.put("energy", states.energy());
            root.put("affection", states.affection());
            root.set("growth", toGrowthNode(growth));
            if (expGained != null) {
                root.put("expGained", expGained);
            }
            if (levelUp != null) {
                root.put("levelUp", levelUp);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("돌봄 상태 스냅샷 직렬화에 실패했습니다.", e);
        }
    }

    private ObjectNode toGrowthNode(p5laris.character.domain.application.dto.CharacterGrowthResponse growth) {
        p5laris.character.domain.application.dto.CharacterGrowthResponse safeGrowth =
                growth != null ? growth : toGrowthResponse(CharacterGrowthPolicy.calculate(0));
        ObjectNode growthNode = objectMapper.createObjectNode();
        growthNode.put("level", safeGrowth.level());
        growthNode.put("exp", safeGrowth.exp());
        growthNode.put("currentLevelExp", safeGrowth.currentLevelExp());
        growthNode.put("nextLevelExp", safeGrowth.nextLevelExp());
        growthNode.put("expToNextLevel", safeGrowth.expToNextLevel());
        growthNode.put("progressPercent", safeGrowth.progressPercent());
        growthNode.put("growthStage", safeGrowth.growthStage());
        growthNode.put("growthStageLabel", safeGrowth.growthStageLabel());
        growthNode.put("maxLevel", safeGrowth.maxLevel());
        return growthNode;
    }

    private p5laris.character.domain.application.dto.CareActionResponse.States parseStates(JsonNode node) {
        return p5laris.character.domain.application.dto.CareActionResponse.States.builder()
                .hunger(node.path("fullness").asInt())
                .energy(node.path("energy").asInt())
                .affection(node.path("affection").asInt())
                .build();
    }

    private p5laris.character.domain.application.dto.CharacterGrowthResponse parseGrowth(JsonNode growthNode) {
        if (growthNode == null || growthNode.isMissingNode() || growthNode.isNull()) {
            return toGrowthResponse(CharacterGrowthPolicy.calculate(0));
        }

        int exp = growthNode.path("exp").asInt(0);
        var fallback = CharacterGrowthPolicy.calculate(exp);
        return p5laris.character.domain.application.dto.CharacterGrowthResponse.builder()
                .level(growthNode.path("level").asInt(fallback.level()))
                .exp(exp)
                .currentLevelExp(growthNode.path("currentLevelExp").asInt(fallback.currentLevelExp()))
                .nextLevelExp(growthNode.path("nextLevelExp").asInt(fallback.nextLevelExp()))
                .expToNextLevel(growthNode.path("expToNextLevel").asInt(fallback.expToNextLevel()))
                .progressPercent(growthNode.path("progressPercent").asInt(fallback.progressPercent()))
                .growthStage(growthNode.path("growthStage").asText(fallback.growthStage().name()))
                .growthStageLabel(growthNode.path("growthStageLabel").asText(fallback.growthStageLabel()))
                .maxLevel(growthNode.path("maxLevel").asBoolean(fallback.maxLevel()))
                .build();
    }

    private p5laris.character.domain.application.dto.CharacterGrowthResponse toGrowthResponse(
            CharacterGrowthPolicy.Growth growth
    ) {
        return p5laris.character.domain.application.dto.CharacterGrowthResponse.builder()
                .level(growth.level())
                .exp(growth.exp())
                .currentLevelExp(growth.currentLevelExp())
                .nextLevelExp(growth.nextLevelExp())
                .expToNextLevel(growth.expToNextLevel())
                .progressPercent(growth.progressPercent())
                .growthStage(growth.growthStage().name())
                .growthStageLabel(growth.growthStageLabel())
                .maxLevel(growth.maxLevel())
                .build();
    }

    private String characterMessage(ActionType actionType) {
        return switch (actionType) {
            case FEED -> "먹는 중... 빛도 맛이 있구나.";
            case SLEEP -> "...zz. 고마워요.";
            case PLAY -> "재밌었어요. 다음에 또 놀아요.";
        };
    }
}
