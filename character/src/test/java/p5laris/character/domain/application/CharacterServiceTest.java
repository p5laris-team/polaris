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
    private com.p5laris.proto.item.v1.ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CharacterService characterService;

    private UserCharacter character;

    @BeforeEach
    void setUp() {
        character = UserCharacter.builder()
                .userId(1L)
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
        assertEquals("음… 오늘의 빛은 좀 달콤하네요.", response.characterMessage());

        // Verify no repository interactions for userCharacter
        verifyNoInteractions(userCharacterRepository);
        verify(characterCareLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Successful care action performance for new idempotency key")
    void testPerformCareAction_NewIdempotencyKey() {
        String key = "new-idempotency-key";
        
        when(characterCareLogRepository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(userCharacterRepository.findById(1L)).thenReturn(Optional.of(character));

        CareActionResponse response = characterService.performCareAction(1L, 1L, "FEED", null, key);

        assertNotNull(response);
        assertEquals("FEED", response.actionType());
        assertEquals(50, response.beforeStates().hunger());
        assertEquals(80, response.afterStates().hunger()); // +30 fullness recovery
        
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
}
