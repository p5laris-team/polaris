package p5laris.gateway.domain.character.infrastructure.grpc;

import com.p5laris.proto.character.v1.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.character.api.dto.CharacterTypesResponse;

import java.util.List;

@Service
public class CharacterGatewayService {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    // 기존 스캐폴딩 (핑퐁)
    public String getCharacter(String value) {
        PingPongResponse response = characterStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );
        return response.getMessage();
    }

    /**
     * Get character types (API spec 4.1).
     */
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

    /**
     * Get character assets (API spec 4.2).
     */
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

    /**
     * Create a user character (API spec 4.3).
     */
    public p5laris.gateway.domain.character.api.dto.UserCharacterResponse createCharacter(
            Long userId, p5laris.gateway.domain.character.api.dto.CreateCharacterRequest request) {
        
        com.p5laris.proto.character.v1.CreateCharacterResponse response = characterStub.createCharacter(
                com.p5laris.proto.character.v1.CreateCharacterRequest.newBuilder()
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

    // TODO [Item Domain Integration]: ItemGatewayService 주입 필요 (현재 주석 처리)
    // private final p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService itemGatewayService;

    // public CharacterGatewayService(ItemGatewayService itemGatewayService) {
    //     this.itemGatewayService = itemGatewayService;
    // }
    // 위 주입은 Lombok의 @RequiredArgsConstructor를 사용하려면 필드에 선언하면 됩니다.

    /**
     * Get my character (API spec 4.4).
     */
    public p5laris.gateway.domain.character.api.dto.MyCharacterResponse getMyCharacter(Long userId) {
        com.p5laris.proto.character.v1.GetMyCharacterResponse response = characterStub.getMyCharacter(
                com.p5laris.proto.character.v1.GetMyCharacterRequest.newBuilder()
                        .setUserId(userId)
                        .build()
        );

        p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin skin = null;
        if (response.getEquippedSkinId() > 0) {
            
            // TODO [Item Domain Integration]: 아이템 모듈에서 스킨 이름 조회. 
            // 구현 후 아래 주석을 해제하고, 하단의 임시(Mock) 코드를 삭제할 것.
            /*
            var itemResponse = itemGatewayService.getItem(response.getEquippedSkinId());
            skin = p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin.builder()
                    .itemId(itemResponse.getId())
                    .name(itemResponse.getName())
                    .build();
            */

            // 임시(Mock) 코드: Item 도메인 연동 전까지 컴파일 에러 방지용
            skin = p5laris.gateway.domain.character.api.dto.MyCharacterResponse.EquippedSkin.builder()
                    .itemId(response.getEquippedSkinId())
                    .name("스킨 " + response.getEquippedSkinId())
                    .build();
        }

        return p5laris.gateway.domain.character.api.dto.MyCharacterResponse.builder()
                .id(response.getId())
                .name(response.getName())
                .characterTypeCode(response.getCharacterTypeCode())
                .active(response.getActive())
                .equippedSkin(skin)
                .build();
    }

    /**
     * Update character name (API spec 4.5).
     */
    public p5laris.gateway.domain.character.api.dto.UpdateCharacterNameResponse updateCharacterName(
            Long characterId, Long userId, p5laris.gateway.domain.character.api.dto.UpdateCharacterNameRequest request) {
        
        com.p5laris.proto.character.v1.UpdateCharacterNameResponse response = characterStub.updateCharacterName(
                com.p5laris.proto.character.v1.UpdateCharacterNameRequest.newBuilder()
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

    /**
     * Get character status (API spec 4.6).
     */
    public p5laris.gateway.domain.character.api.dto.CharacterStatusResponse getCharacterStatus(Long characterId, Long userId) {
        com.p5laris.proto.character.v1.GetCharacterStatusResponse response = characterStub.getCharacterStatus(
                com.p5laris.proto.character.v1.GetCharacterStatusRequest.newBuilder()
                        .setCharacterId(characterId)
                        .setUserId(userId)
                        .build()
        );

        return p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.builder()
                .characterId(response.getCharacterId())
                .states(p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.States.builder()
                        .hunger(p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail.builder()
                                .value(response.getHunger().getValue())
                                .label(response.getHunger().getLabel())
                                .grade(response.getHunger().getGrade())
                                .build())
                        .energy(p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail.builder()
                                .value(response.getEnergy().getValue())
                                .label(response.getEnergy().getLabel())
                                .grade(response.getEnergy().getGrade())
                                .build())
                        .affection(p5laris.gateway.domain.character.api.dto.CharacterStatusResponse.StateDetail.builder()
                                .value(response.getAffection().getValue())
                                .label(response.getAffection().getLabel())
                                .grade(response.getAffection().getGrade())
                                .build())
                        .build())
                .build();
    }

    /**
     * Perform care action (API spec 4.7).
     */
    public p5laris.gateway.domain.character.api.dto.CareActionResponse performCareAction(
            Long characterId, Long userId,
            p5laris.gateway.domain.character.api.dto.CareActionRequest request) {

        com.p5laris.proto.character.v1.PerformCareActionResponse response =
                characterStub.performCareAction(
                        com.p5laris.proto.character.v1.PerformCareActionRequest.newBuilder()
                                .setCharacterId(characterId)
                                .setUserId(userId)
                                .setActionType(request.actionType())
                                .setItemId(request.itemId() != null ? request.itemId() : 0L)
                                .build()
                );

        // itemId=0 means no item was consumed
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
}

