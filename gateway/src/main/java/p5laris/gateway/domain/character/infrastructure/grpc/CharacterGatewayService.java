package p5laris.gateway.domain.character.infrastructure.grpc;

import com.p5laris.proto.character.v1.*;
import com.p5laris.proto.item.v1.UserItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.character.api.dto.CharacterTypesResponse;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterGatewayService {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    private final ItemGatewayService itemGatewayService;

    @Value("${asset.cdn-base-url}")
    private String cdnBaseUrl;

    public String getCharacter(String value) {
        PingPongResponse response = characterStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );
        return response.getMessage();
    }

    public CharacterTypesResponse getCharacterTypes() {
        GetCharacterTypesResponse response = characterStub.getCharacterTypes(
                GetCharacterTypesRequest.newBuilder().build()
        );

        List<CharacterTypesResponse.CharacterTypeItem> items = response.getItemsList()
                .stream()
                .map(item -> CharacterTypesResponse.CharacterTypeItem.builder()
                        .id(item.getId())
                        .code(item.getCode())
                        .name(item.getName())
                        .summary(item.getSummary())
                        .sampleLine(item.getSampleLine())
                        .sortOrder(item.getSortOrder())
                        .build())
                .toList();

        return CharacterTypesResponse.builder()
                .items(items)
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse getCharacterAssets(Long characterTypeId) {
        GetCharacterAssetsResponse response = characterStub.getCharacterAssets(
                GetCharacterAssetsRequest.newBuilder()
                        .setCharacterTypeId(characterTypeId)
                        .build()
        );

        List<p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse.AssetItem> items = response.getItemsList()
                .stream()
                .map(item -> p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse.AssetItem.builder()
                        .assetType(item.getAssetType())
                        .assetUrl(item.getAssetUrl())
                        .build())
                .toList();

        return p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse.builder()
                .characterTypeId(response.getCharacterTypeId())
                .items(items)
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.UserCharacterResponse createCharacter(
            Long userId, p5laris.gateway.domain.character.api.dto.CreateCharacterRequest request) {
        CreateCharacterResponse response = characterStub.createCharacter(
                CreateCharacterRequest.newBuilder()
                        .setUserId(userId)
                        .setCharacterTypeId(request.characterTypeId())
                        .setName(request.name())
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.UserCharacterResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .characterTypeCode(response.getCharacterTypeCode())
                .active(response.getActive())
                .states(p5laris.gateway.domain.character.api.dto.UserCharacterResponse.States.builder()
                        .hunger(response.getStates().getHunger())
                        .energy(response.getStates().getEnergy())
                        .affection(response.getStates().getAffection())
                        .build())
                .createdAt(java.time.Instant.parse(response.getCreatedAt()))
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.MyCharacterResponse getMyCharacter(Long userId) {
        GetMyCharacterResponse response = characterStub.getMyCharacter(
                GetMyCharacterRequest.newBuilder()
                        .setUserId(userId)
                        .build()
        );

        p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin skin = null;
        if (response.getEquippedSkinId() > 0) {
            skin = p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin.builder()
                    .itemId(response.getEquippedSkinId())
                    .name(resolveSkinName(userId, response.getEquippedSkinId()))
                    .build();
        }

        Long characterTypeId = null;
        try {
            var typesResponse = getCharacterTypes();
            var matchedType = typesResponse.items().stream()
                    .filter(t -> t.code().equalsIgnoreCase(response.getCharacterTypeCode()))
                    .findFirst();
            if (matchedType.isPresent()) {
                characterTypeId = matchedType.get().id();
            }
        } catch (Exception e) {
            log.warn("Failed to get character type id for code: {}. Error: {}", response.getCharacterTypeCode(), e.getMessage());
        }

        java.util.Map<String, String> assetUrls = new java.util.HashMap<>();
        if (response.getEquippedSkinId() > 0 && characterTypeId != null) {
            try {
                assetUrls = itemGatewayService.getSkinAssets(response.getEquippedSkinId(), characterTypeId);
            } catch (Exception e) {
                log.warn("Failed to get skin assets for skinId: {}, charTypeId: {}. Error: {}", 
                        response.getEquippedSkinId(), characterTypeId, e.getMessage());
            }
        }

        if (assetUrls == null || assetUrls.isEmpty()) {
            assetUrls = new java.util.HashMap<>();
            String typeCode = response.getCharacterTypeCode();
            String pathType = typeCode.toLowerCase();
            if ("JJORY".equalsIgnoreCase(typeCode)) {
                pathType = "jjori";
            }

            String baseUrl = cdnBaseUrl != null ? cdnBaseUrl.trim() : "https://d24c6my56k1w5v.cloudfront.net";
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            assetUrls.put("idle", baseUrl + "/assets/characters/" + pathType + "/core/character-" + pathType + "-idle-v1.png");
            assetUrls.put("happy", baseUrl + "/assets/characters/" + pathType + "/core/character-" + pathType + "-happy-v1.png");
            assetUrls.put("sleepy", baseUrl + "/assets/characters/" + pathType + "/core/character-" + pathType + "-sleepy-v1.png");

            assetUrls.put("hungry", baseUrl + "/assets/characters/" + pathType + "/status/character-" + pathType + "-hungry-v1.png");
            assetUrls.put("lowEnergy", baseUrl + "/assets/characters/" + pathType + "/status/character-" + pathType + "-low-energy-v1.png");
            assetUrls.put("lonely", baseUrl + "/assets/characters/" + pathType + "/status/character-" + pathType + "-lonely-v1.png");
        }

        String currentAssetUrl = assetUrls.getOrDefault("idle", "");

        return p5laris.gateway.domain.character.api.dto.MyCharacterResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .characterTypeCode(response.getCharacterTypeCode())
                .currentAssetUrl(currentAssetUrl)
                .assetUrls(assetUrls)
                .active(response.getActive())
                .equippedSkin(skin)
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.UpdateCharacterNameResponse updateCharacterName(
            Long characterId, Long userId, p5laris.gateway.domain.character.api.dto.UpdateCharacterNameRequest request) {
        UpdateCharacterNameResponse response = characterStub.updateCharacterName(
                UpdateCharacterNameRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .setName(request.name())
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.UpdateCharacterNameResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .updatedAt(java.time.Instant.parse(response.getUpdatedAt()))
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CharacterStatusResponse getCharacterStatus(Long characterId, Long userId) {
        GetCharacterStatusResponse response = characterStub.getCharacterStatus(
                GetCharacterStatusRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.builder()
                .characterId(response.getCharacterId())
                .states(p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.States.builder()
                        .hunger(toStateDetail(response.getHunger()))
                        .energy(toStateDetail(response.getEnergy()))
                        .affection(toStateDetail(response.getAffection()))
                        .build())
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.CareActionResponse performCareAction(
            Long characterId, Long userId, String idempotencyKey,
            p5laris.gateway.domain.character.api.dto.CareActionRequest request) {
        PerformCareActionResponse response = characterStub.performCareAction(
                PerformCareActionRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .setActionType(request.actionType())
                        .setItemId(request.itemId() != null ? request.itemId() : 0L)
                        .setIdempotencyKey(idempotencyKey != null ? idempotencyKey : "")
                        .build()
        );

        Long consumedItemId = response.getConsumedItemId() > 0 ? response.getConsumedItemId() : null;

        return p5laris.gateway.domain.character.api.dto.CareActionResponse.builder()
                .careLogId(response.getCareLogId())
                .characterId(response.getCharacterId())
                .actionType(response.getActionType())
                .consumed(p5laris.gateway.domain.character.api.dto.CareActionResponse.Consumed.builder()
                        .itemId(consumedItemId)
                        .quantity(response.getConsumedQuantity())
                        .build())
                .beforeStates(p5laris.gateway.domain.character.api.dto.CareActionResponse.States.builder()
                        .hunger(response.getBeforeStates().getHunger())
                        .energy(response.getBeforeStates().getEnergy())
                        .affection(response.getBeforeStates().getAffection())
                        .build())
                .afterStates(p5laris.gateway.domain.character.api.dto.CareActionResponse.States.builder()
                        .hunger(response.getAfterStates().getHunger())
                        .energy(response.getAfterStates().getEnergy())
                        .affection(response.getAfterStates().getAffection())
                        .build())
                .characterMessage(response.getCharacterMessage())
                .build();
    }

    public p5laris.gateway.domain.character.api.dto.EquipSkinResponse equipSkin(
            Long characterId,
            Long userId,
            p5laris.gateway.domain.character.api.dto.EquipSkinRequest request) {
        EquipSkinRequest.Builder grpcRequest = EquipSkinRequest.newBuilder()
                .setCharacterId(characterId)
                .setUserId(userId);

        if (request.itemId() != null) {
            grpcRequest.setItemId(request.itemId());
        }

        EquipSkinResponse response = characterStub.equipSkin(grpcRequest.build());

        p5laris.gateway.domain.character.api.dto.EquipSkinResponse.EquippedSkin equippedSkin = null;
        if (response.getEquippedSkinId() > 0) {
            equippedSkin = p5laris.gateway.domain.character.api.dto.EquipSkinResponse.EquippedSkin.builder()
                    .itemId(response.getEquippedSkinId())
                    .name(resolveSkinName(userId, response.getEquippedSkinId()))
                    .build();
        }

        return p5laris.gateway.domain.character.api.dto.EquipSkinResponse.builder()
                .characterId(response.getCharacterId())
                .equippedSkin(equippedSkin)
                .updatedAt(java.time.Instant.parse(response.getUpdatedAt()))
                .build();
    }

    private p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail toStateDetail(
            com.p5laris.proto.character.v1.CharacterStateDetail state) {
        return p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail.builder()
                .value(state.getValue())
                .label(state.getLabel())
                .grade(state.getGrade())
                .build();
    }

    private String resolveSkinName(Long userId, Long itemId) {
        try {
            return itemGatewayService.getUserItems(userId, "SKIN", "", 100)
                    .getItemsList()
                    .stream()
                    .filter(item -> item.getItemId() == itemId)
                    .findFirst()
                    .map(UserItem::getName)
                    .orElse("Skin " + itemId);
        } catch (Exception e) {
            return "Skin " + itemId;
        }
    }
}
