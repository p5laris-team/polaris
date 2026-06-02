package p5laris.character.domain.api;

import com.p5laris.proto.character.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.character.domain.application.CharacterService;
import p5laris.character.domain.application.ShareService;

@GrpcService
@RequiredArgsConstructor
public class CharacterGrpcController extends CharacterServiceGrpc.CharacterServiceImplBase {

    private final CharacterService characterService;
    private final ShareService shareService;

    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Get character types (API spec 4.1).
     * Returns active character types sorted by sort_order ascending.
     */
    @Override
    public void getCharacterTypes(GetCharacterTypesRequest request,
                                  StreamObserver<GetCharacterTypesResponse> responseObserver) {
        var items = characterService.getCharacterTypes()
                .stream()
                .map(ct -> CharacterTypeItem.newBuilder()
                        .setId(ct.id())
                        .setCode(ct.code())
                        .setName(ct.name())
                        .setSummary(ct.summary())
                        .setSampleLine(ct.sampleLine())
                        .setSortOrder(ct.sortOrder())
                        .build())
                .toList();

        GetCharacterTypesResponse response = GetCharacterTypesResponse.newBuilder()
                .addAllItems(items)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Get character assets (API spec 4.2).
     */
    @Override
    public void getCharacterAssets(GetCharacterAssetsRequest request,
                                   StreamObserver<GetCharacterAssetsResponse> responseObserver) {
        var items = characterService.getCharacterAssets(request.getCharacterTypeId())
                .stream()
                .map(a -> CharacterAssetItem.newBuilder()
                        .setAssetType(a.assetType())
                        .setAssetUrl(a.assetUrl())
                        .build())
                .toList();

        GetCharacterAssetsResponse response = GetCharacterAssetsResponse.newBuilder()
                .setCharacterTypeId(request.getCharacterTypeId())
                .addAllItems(items)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Create a user character (API spec 4.3).
     */
    @Override
    public void createCharacter(CreateCharacterRequest request,
                                StreamObserver<CreateCharacterResponse> responseObserver) {
        var result = characterService.createCharacter(
                request.getUserId(),
                request.getCharacterTypeId(),
                request.getName()
        );

        CreateCharacterResponse response = CreateCharacterResponse.newBuilder()
                .setId(result.id())
                .setName(result.name())
                .setCharacterTypeCode(result.characterTypeCode())
                .setActive(result.active())
                .setStates(CharacterStates.newBuilder()
                        .setHunger(result.states().hunger())
                        .setEnergy(result.states().energy())
                        .setAffection(result.states().affection())
                        .build())
                .setCreatedAt(result.createdAt().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Get my character (API spec 4.4).
     */
    @Override
    public void getMyCharacter(GetMyCharacterRequest request,
                               StreamObserver<GetMyCharacterResponse> responseObserver) {
        var result = characterService.getMyCharacter(request.getUserId());

        GetMyCharacterResponse response = GetMyCharacterResponse.newBuilder()
                .setId(result.id())
                .setName(result.name())
                .setCharacterTypeCode(result.characterTypeCode())
                .setActive(result.active())
                .setEquippedSkinId(result.equippedSkinId())
                .setStates(CharacterStates.newBuilder()
                        .setHunger(result.states().hunger())
                        .setEnergy(result.states().energy())
                        .setAffection(result.states().affection())
                        .build())
                .setCurrentAssetUrl(result.currentAssetUrl() != null ? result.currentAssetUrl() : "")
                .putAllAssetUrls(result.assetUrls() != null ? result.assetUrls() : java.util.Map.of())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Update character name (API spec 4.5).
     */
    @Override
    public void updateCharacterName(UpdateCharacterNameRequest request,
                                    StreamObserver<UpdateCharacterNameResponse> responseObserver) {
        var result = characterService.updateCharacterName(
                request.getCharacterId(),
                request.getUserId(),
                request.getName()
        );

        UpdateCharacterNameResponse response = UpdateCharacterNameResponse.newBuilder()
                .setId(result.id())
                .setName(result.name())
                .setUpdatedAt(result.updatedAt().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Get character status (API spec 4.6).
     */
    @Override
    public void getCharacterStatus(GetCharacterStatusRequest request,
                                   StreamObserver<GetCharacterStatusResponse> responseObserver) {
        var result = characterService.getCharacterStatus(request.getCharacterId(), request.getUserId());

        GetCharacterStatusResponse response = GetCharacterStatusResponse.newBuilder()
                .setCharacterId(result.characterId())
                .setHunger(com.p5laris.proto.character.v1.CharacterStateDetail.newBuilder()
                        .setValue(result.states().hunger().value())
                        .setLabel(result.states().hunger().label())
                        .setGrade(result.states().hunger().grade())
                        .build())
                .setEnergy(com.p5laris.proto.character.v1.CharacterStateDetail.newBuilder()
                        .setValue(result.states().energy().value())
                        .setLabel(result.states().energy().label())
                        .setGrade(result.states().energy().grade())
                        .build())
                .setAffection(com.p5laris.proto.character.v1.CharacterStateDetail.newBuilder()
                        .setValue(result.states().affection().value())
                        .setLabel(result.states().affection().label())
                        .setGrade(result.states().affection().grade())
                        .build())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Perform care action (API spec 4.7).
     */
    @Override
    public void performCareAction(PerformCareActionRequest request,
                                  StreamObserver<PerformCareActionResponse> responseObserver) {
        var result = characterService.performCareAction(
                request.getCharacterId(),
                request.getUserId(),
                request.getActionType(),
                request.getItemId(),
                request.getIdempotencyKey()
        );

        PerformCareActionResponse response = PerformCareActionResponse.newBuilder()
                .setCareLogId(result.careLogId() != null ? result.careLogId() : 0L)
                .setCharacterId(result.characterId())
                .setActionType(result.actionType())
                .setConsumedItemId(result.consumedItemId() != null ? result.consumedItemId() : 0L)
                .setConsumedQuantity(result.consumedQuantity())
                .setBeforeStates(CharacterStates.newBuilder()
                        .setHunger(result.beforeStates().hunger())
                        .setEnergy(result.beforeStates().energy())
                        .setAffection(result.beforeStates().affection())
                        .build())
                .setAfterStates(CharacterStates.newBuilder()
                        .setHunger(result.afterStates().hunger())
                        .setEnergy(result.afterStates().energy())
                        .setAffection(result.afterStates().affection())
                        .build())
                .setCharacterMessage(result.characterMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Equip skin on character (API spec 4.8).
     */
    @Override
    public void equipSkin(EquipSkinRequest request,
                          StreamObserver<EquipSkinResponse> responseObserver) {
        var result = characterService.equipSkin(
                request.getCharacterId(),
                request.getUserId(),
                request.hasItemId() ? request.getItemId() : null
        );

        EquipSkinResponse response = EquipSkinResponse.newBuilder()
                .setCharacterId(result.characterId())
                .setEquippedSkinId(result.equippedSkinId() != null ? result.equippedSkinId() : 0L)
                .setUpdatedAt(result.updatedAt().toString())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // ---------- Share APIs (§9) ----------

    @Override
    public void createShareCard(CreateShareCardRequest request,
                                StreamObserver<CreateShareCardResponse> responseObserver) {
        var result = shareService.createShareCard(
                request.getUserId(), 
                request.getCharacterId(), 
                request.getHeadline(),
                request.getImageUrl()
        );

        responseObserver.onNext(CreateShareCardResponse.newBuilder()
                .setShareCardId(result.shareCardId())
                .setShareId(result.shareId())
                .setImageUrl(result.imageUrl() != null ? result.imageUrl() : "")
                .setShareUrl(result.shareUrl())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getShareCard(GetShareCardRequest request,
                             StreamObserver<GetShareCardResponse> responseObserver) {
        var result = shareService.getShareCard(request.getShareCardId(), request.getUserId());

        responseObserver.onNext(GetShareCardResponse.newBuilder()
                .setShareCardId(result.shareCardId())
                .setCharacterName(result.characterName())
                .setImageUrl(result.imageUrl() != null ? result.imageUrl() : "")
                .setShareUrl(result.shareUrl())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void createShareEvent(CreateShareEventRequest request,
                                 StreamObserver<CreateShareEventResponse> responseObserver) {
        var result = shareService.createShareEvent(
                request.getUserId(),
                request.getShareCardId(),
                request.getPlatform(),
                request.getShareType(),
                request.getIdempotencyKey()
        );

        responseObserver.onNext(CreateShareEventResponse.newBuilder()
                .setShareEventId(result.shareEventId())
                .setRewardPaid(result.rewardPaid())
                .setRewardStarPiece(result.rewardStarPiece())
                .setWalletStarPiece(result.walletStarPiece())
                .setRewardStatus(result.rewardStatus().name())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getShareLink(GetShareLinkRequest request,
                             StreamObserver<GetShareLinkResponse> responseObserver) {
        var result = shareService.getShareLink(request.getShareId());

        responseObserver.onNext(GetShareLinkResponse.newBuilder()
                .setShareId(result.shareId())
                .setCharacterName(result.characterName())
                .setImageUrl(result.imageUrl() != null ? result.imageUrl() : "")
                .setHeadline(result.headline())
                .setSignupUrl(result.signupUrl())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void recordShareClick(RecordShareClickRequest request,
                                 StreamObserver<RecordShareClickResponse> responseObserver) {
        var result = shareService.recordShareClick(
                request.getShareId(),
                request.getReferrer(),
                request.getUtmSource(),
                request.getUtmMedium(),
                request.getUtmCampaign()
        );

        responseObserver.onNext(RecordShareClickResponse.newBuilder()
                .setShareId(result.shareId())
                .setRecorded(result.recorded())
                .build());
        responseObserver.onCompleted();
    }
    @Override
    public void getSharePresignedUrl(com.p5laris.proto.character.v1.GetSharePresignedUrlRequest request,
                                     io.grpc.stub.StreamObserver<com.p5laris.proto.character.v1.GetSharePresignedUrlResponse> responseObserver) {
        var result = shareService.getSharePresignedUrl(
                request.getUserId(),
                request.getExtension()
        );

        responseObserver.onNext(com.p5laris.proto.character.v1.GetSharePresignedUrlResponse.newBuilder()
                .setPresignedUrl(result.presignedUrl())
                .setImageUrl(result.imageUrl())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTodayShareEventStatus(GetTodayShareEventStatusRequest request,
                                         StreamObserver<GetTodayShareEventStatusResponse> responseObserver) {
        var result = shareService.getTodayShareEventStatus(request.getUserId());
        responseObserver.onNext(GetTodayShareEventStatusResponse.newBuilder()
                .setRewardClaimed(result.rewardClaimed())
                .setLastSharedAt(result.lastSharedAt() != null ? result.lastSharedAt() : "")
                .setRewardStatus(result.rewardStatus() != null ? result.rewardStatus() : "")
                .build());
        responseObserver.onCompleted();
    }
}

