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
}

