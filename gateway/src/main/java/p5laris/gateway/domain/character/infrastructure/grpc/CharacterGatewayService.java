package p5laris.gateway.domain.character.infrastructure.grpc;

import com.p5laris.proto.character.v1.*;
import com.p5laris.proto.item.v1.UserItem;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.character.api.dto.CharacterTypesResponse;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterGatewayService {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    private final ItemGatewayService itemGatewayService;

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
                .growth(toGrowth(response.getGrowth()))
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

        return p5laris.gateway.domain.character.api.dto.MyCharacterResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .characterTypeCode(response.getCharacterTypeCode())
                .currentAssetUrl(response.getCurrentAssetUrl())
                .assetUrls(response.getAssetUrlsMap())
                .active(response.getActive())
                .states(p5laris.gateway.domain.character.api.dto.MyCharacterResponse.States.builder()
                        .hunger(response.getStates().getHunger())
                        .energy(response.getStates().getEnergy())
                        .affection(response.getStates().getAffection())
                        .build())
                .growth(toGrowth(response.getGrowth()))
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
                .growth(toGrowth(response.getGrowth()))
                .build();
    }

    private p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse toGrowth(CharacterGrowth growth) {
        return p5laris.gateway.domain.character.api.dto.CharacterGrowthResponse.builder()
                .level(growth.getLevel())
                .exp(growth.getExp())
                .currentLevelExp(growth.getCurrentLevelExp())
                .nextLevelExp(growth.getNextLevelExp())
                .expToNextLevel(growth.getExpToNextLevel())
                .progressPercent(growth.getProgressPercent())
                .growthStage(growth.getGrowthStage())
                .growthStageLabel(growth.getGrowthStageLabel())
                .maxLevel(growth.getMaxLevel())
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
                .beforeGrowth(toGrowth(response.getBeforeGrowth()))
                .afterGrowth(toGrowth(response.getAfterGrowth()))
                .expGained(response.getExpGained())
                .levelUp(response.getLevelUp())
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
