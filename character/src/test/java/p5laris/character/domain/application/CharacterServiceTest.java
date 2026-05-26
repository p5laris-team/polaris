package p5laris.character.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import p5laris.character.domain.application.dto.CareActionResponse;
import p5laris.character.domain.domain.entity.CharacterCareLog;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.ActionType;
import p5laris.character.domain.domain.repository.CharacterAssetRepository;
import p5laris.character.domain.domain.repository.CharacterCareLogRepository;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;

import p5laris.character.domain.domain.entity.CharacterAsset;
import p5laris.character.domain.domain.entity.CharacterType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private CharacterTypeRepository characterTypeRepository;

    @Mock
    private CharacterAssetRepository characterAssetRepository;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private CharacterCareLogRepository characterCareLogRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private com.p5laris.proto.item.v1.ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CharacterService characterService;

    private UserCharacter character;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(characterService, "itemStub", itemStub);
        lenient().when(s3StorageService.toPublicUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CharacterType characterType = CharacterType.builder()
                .code("NOVA")
                .name("Nova")
                .active(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(characterType, "id", 1L);

        character = UserCharacter.builder()
                .userId(1L)
                .characterType(characterType)
                .name("Nova")
                .level(1)
                .exp(0)
                .fullness(50)
                .energy(50)
                .affection(50)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Idempotency key null or empty validation")
    void testPerformCareAction_IdempotencyKeyNullOrEmpty() {
        // null key
        assertThrows(IllegalArgumentException.class, () ->
                characterService.performCareAction(1L, 1L, "FEED", null, null)
        );

        // empty key
        assertThrows(IllegalArgumentException.class, () ->
                characterService.performCareAction(1L, 1L, "FEED", null, "")
        );

        // whitespace key
        assertThrows(IllegalArgumentException.class, () ->
                characterService.performCareAction(1L, 1L, "FEED", null, "   ")
        );
    }

    @Test
    @DisplayName("Cached care log is returned for existing idempotency key")
    void testPerformCareAction_ExistingIdempotencyKey() {
        String key = "test-idempotency-key";
        CharacterCareLog cachedLog = CharacterCareLog.builder()
                .userId(1L)
                .characterId(1L)
                .itemId(null)
                .actionType(ActionType.FEED)
                .beforeStateJson("{\"fullness\":50,\"energy\":50,\"affection\":50}")
                .afterStateJson("{\"fullness\":80,\"energy\":50,\"affection\":50}")
                .idempotencyKey(key)
                .build();

        // Reflection or using mock to return ID
        // (For simplicity we assume getId() returns null or we can mock/stub, but standard entity field getter works)
        
        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(cachedLog));

        CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", null, key);

        assertNotNull(response);
        assertEquals("FEED", response.actionType());
        assertEquals(50, response.beforeStates().hunger());
        assertEquals(80, response.afterStates().hunger());
        assertEquals("먹는 중... 빛도 맛이 있구나.", response.characterMessage());

        // Verify no repository interactions for userCharacter
        verifyNoInteractions(userCharacterRepository);
        verify(characterCareLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Successful care action performance for new idempotency key")
    void testPerformCareAction_NewIdempotencyKey() {
        String key = "new-idempotency-key";
        Long itemId = 10L;
        
        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findById(1L)).thenReturn(Optional.of(character));

        CharacterCareLog savedCareLog = CharacterCareLog.builder()
                .userId(1L)
                .characterId(1L)
                .itemId(itemId)
                .actionType(ActionType.FEED)
                .beforeStateJson("{\"fullness\":50,\"energy\":50,\"affection\":50}")
                .afterStateJson("{\"fullness\":80,\"energy\":50,\"affection\":50}")
                .idempotencyKey(key)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(savedCareLog, "id", 123L);
        when(characterCareLogRepository.save(any(CharacterCareLog.class))).thenReturn(savedCareLog);

        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("맛있는 사료")
                .setItemType("CONSUMABLE")
                .setEffectType("FOOD")
                .setQuantity(5)
                .build();
        com.p5laris.proto.item.v1.GetUserItemsResponse ownedItems = com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                .addItems(userItem)
                .build();
        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(ownedItems);

        com.p5laris.proto.item.v1.UseItemResponse useItemResponse = com.p5laris.proto.item.v1.UseItemResponse.newBuilder()
                .setUsageId(1L)
                .setUserItemId(100L)
                .setItemId(itemId)
                .setQuantityUsed(1)
                .setRemainingQuantity(4)
                .build();
        when(itemStub.useItem(any(com.p5laris.proto.item.v1.UseItemRequest.class)))
                .thenReturn(useItemResponse);

        try {
            CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", itemId, key);
            assertNotNull(response);
            assertEquals("FEED", response.actionType());
            assertEquals(50, response.beforeStates().hunger());
            assertEquals(80, response.afterStates().hunger()); // +30 fullness recovery
        } catch (p5laris.character.domain.exception.CharacterException e) {
            System.out.println("DEBUG - CharacterException thrown with code: " + e.getErrorCode());
            throw e;
        }
        
        // Verify database saves the care log
        verify(characterCareLogRepository).save(any(CharacterCareLog.class));
    }

    @Test
    @DisplayName("equipSkin - 보유한 스킨의 경우 정상 장착")
    void equipSkin_ownedSkin_success() {
        // given
        Long characterId = 1L;
        Long userId = 1L;
        Long itemId = 10L;

        when(userCharacterRepository.findById(characterId)).thenReturn(Optional.of(character));

        com.p5laris.proto.item.v1.UserItem userItem = com.p5laris.proto.item.v1.UserItem.newBuilder()
                .setItemId(itemId)
                .setName("푸른 새벽 스킨")
                .build();

        com.p5laris.proto.item.v1.GetUserItemsResponse ownedItems = com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                .addItems(userItem)
                .build();

        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(ownedItems);

        // when
        var response = characterService.equipSkin(characterId, userId, itemId);

        // then
        assertNotNull(response);
        assertEquals(itemId, response.equippedSkinId());
        assertEquals(itemId, character.getEquippedSkinId());
    }

    @Test
    @DisplayName("equipSkin - 보유하지 않은 스킨의 경우 예외 발생")
    void equipSkin_notOwnedSkin_throwsException() {
        // given
        Long characterId = 1L;
        Long userId = 1L;
        Long itemId = 10L;

        when(userCharacterRepository.findById(characterId)).thenReturn(Optional.of(character));

        com.p5laris.proto.item.v1.GetUserItemsResponse emptyItems = com.p5laris.proto.item.v1.GetUserItemsResponse.newBuilder()
                .build();

        when(itemStub.getUserItems(any(com.p5laris.proto.item.v1.GetUserItemsRequest.class)))
                .thenReturn(emptyItems);

        // when & then
        p5laris.character.domain.exception.CharacterException exception = assertThrows(
                p5laris.character.domain.exception.CharacterException.class,
                () -> characterService.equipSkin(characterId, userId, itemId)
        );
        assertEquals(p5laris.character.domain.exception.CharacterErrorCode.ITEM_NOT_OWNED, exception.getErrorCode());
    }

    private List<CharacterAsset> createMockAssets(CharacterType type) {
        return List.of(
                CharacterAsset.builder().characterType(type).assetType("IDLE").assetUrl("http://cdn/idle.png").build(),
                CharacterAsset.builder().characterType(type).assetType("HUNGRY").assetUrl("http://cdn/hungry.png").build(),
                CharacterAsset.builder().characterType(type).assetType("LOW_ENERGY").assetUrl("http://cdn/lowEnergy.png").build(),
                CharacterAsset.builder().characterType(type).assetType("LONELY").assetUrl("http://cdn/lonely.png").build()
        );
    }

    @Test
    @DisplayName("getMyCharacter - 기분이 IDLE 일 때 기본 IDLE 이미지 반환")
    void getMyCharacter_idleMood_returnsIdleUrl() {
        // given
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // when
        var response = characterService.getMyCharacter(1L);

        // then
        assertNotNull(response);
        assertEquals("http://cdn/idle.png", response.currentAssetUrl());
        assertEquals("http://cdn/idle.png", response.assetUrls().get("idle"));
        assertEquals("Nova", response.name());
    }

    @Test
    @DisplayName("getMyCharacter - 기분이 HUNGRY 일 때 (포만감 < 40) HUNGRY 이미지 반환")
    void getMyCharacter_hungryMood_returnsHungryUrl() {
        // given
        org.springframework.test.util.ReflectionTestUtils.setField(character, "fullness", 30);
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // when
        var response = characterService.getMyCharacter(1L);

        // then
        assertNotNull(response);
        assertEquals("http://cdn/hungry.png", response.currentAssetUrl());
    }

    @Test
    @DisplayName("getMyCharacter - 기분이 LOW_ENERGY 일 때 (에너지 < 40) LOW_ENERGY 이미지 반환")
    void getMyCharacter_lowEnergyMood_returnsLowEnergyUrl() {
        // given
        org.springframework.test.util.ReflectionTestUtils.setField(character, "energy", 30);
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // when
        var response = characterService.getMyCharacter(1L);

        // then
        assertNotNull(response);
        assertEquals("http://cdn/lowEnergy.png", response.currentAssetUrl());
    }

    @Test
    @DisplayName("getMyCharacter - 기분이 LONELY 일 때 (애정도 < 40) LONELY 이미지 반환")
    void getMyCharacter_lonelyMood_returnsLonelyUrl() {
        // given
        org.springframework.test.util.ReflectionTestUtils.setField(character, "affection", 30);
        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        // when
        var response = characterService.getMyCharacter(1L);

        // then
        assertNotNull(response);
        assertEquals("http://cdn/lonely.png", response.currentAssetUrl());
    }

    @Test
    @DisplayName("getMyCharacter - 스킨이 장착되어 있고 포만감이 낮을 때, 스킨의 HUNGRY 이미지 반환")
    void getMyCharacter_equippedSkin_returnsSkinHungryUrl() {
        // given
        org.springframework.test.util.ReflectionTestUtils.setField(character, "fullness", 30);
        org.springframework.test.util.ReflectionTestUtils.setField(character, "equippedSkinId", 100L);

        when(userCharacterRepository.findByUserIdAndActiveTrue(1L)).thenReturn(Optional.of(character));
        when(characterAssetRepository.findByCharacterTypeId(1L)).thenReturn(createMockAssets(character.getCharacterType()));

        com.p5laris.proto.item.v1.GetSkinAssetsResponse skinResponse = com.p5laris.proto.item.v1.GetSkinAssetsResponse.newBuilder()
                .putAssetUrls("idle", "http://cdn/skin-idle.png")
                .putAssetUrls("hungry", "http://cdn/skin-hungry.png")
                .build();
        when(itemStub.getSkinAssets(any(com.p5laris.proto.item.v1.GetSkinAssetsRequest.class)))
                .thenReturn(skinResponse);

        // when
        var response = characterService.getMyCharacter(1L);

        // then
        assertNotNull(response);
        assertEquals("http://cdn/skin-hungry.png", response.currentAssetUrl());
        assertEquals("http://cdn/skin-hungry.png", response.assetUrls().get("hungry"));
        assertEquals("http://cdn/skin-idle.png", response.assetUrls().get("idle"));
    }
}
