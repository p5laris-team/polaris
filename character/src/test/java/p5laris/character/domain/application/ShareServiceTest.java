package p5laris.character.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.repository.ShareCardRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareCardRepository shareCardRepository;

    @Mock
    private ShareLogRepository shareLogRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @InjectMocks
    private ShareService shareService;

    @Test
    @DisplayName("createShareCard requires frontend-uploaded imageUrl")
    void createShareCard_withoutImageUrl_throwsException() {
        UserCharacter character = createCharacter(10L, 1L);
        when(userCharacterRepository.findById(10L)).thenReturn(Optional.of(character));

        CharacterException exception = assertThrows(CharacterException.class,
                () -> shareService.createShareCard(1L, 10L, "오늘도 반짝였어요.", ""));

        assertEquals(CharacterErrorCode.INVALID_SHARE_CARD_IMAGE_URL, exception.getErrorCode());
        verify(shareCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("createShareCard stores frontend-uploaded S3 object key")
    void createShareCard_withUploadedImageUrl_success() {
        ReflectionTestUtils.setField(shareService, "publicBaseUrl", "https://p5laris.life");
        UserCharacter character = createCharacter(10L, 1L);
        String imageUrl = "https://d24c6my56k1w5v.cloudfront.net/share-cards/1/card.png";
        String objectKey = "share-cards/1/card.png";

        when(userCharacterRepository.findById(10L)).thenReturn(Optional.of(character));
        when(s3StorageService.publicHost()).thenReturn("d24c6my56k1w5v.cloudfront.net");
        when(s3StorageService.toObjectKey(imageUrl)).thenReturn(objectKey);
        when(s3StorageService.toPublicUrl(objectKey)).thenReturn(imageUrl);
        when(shareCardRepository.findByUserIdAndImageUrl(1L, objectKey)).thenReturn(Optional.empty());
        when(shareCardRepository.save(any(ShareCard.class))).thenAnswer(invocation -> {
            ShareCard card = invocation.getArgument(0);
            ReflectionTestUtils.setField(card, "id", 800L);
            return card;
        });

        var result = shareService.createShareCard(1L, 10L, "오늘도 반짝였어요.", imageUrl);

        assertEquals(800L, result.shareCardId());
        assertNotNull(result.shareId());
        assertEquals(imageUrl, result.imageUrl());
        assertTrue(result.shareUrl().startsWith("https://p5laris.life/share/sh_"));

        ArgumentCaptor<ShareCard> cardCaptor = ArgumentCaptor.forClass(ShareCard.class);
        verify(shareCardRepository).save(cardCaptor.capture());
        assertEquals(objectKey, cardCaptor.getValue().getImageUrl());
    }

    private UserCharacter createCharacter(Long characterId, Long userId) {
        CharacterType characterType = CharacterType.builder()
                .code("NOVA")
                .name("Nova")
                .summary("summary")
                .personality("personality")
                .speechStyle("speech")
                .introMessage("intro")
                .sampleLine("sample")
                .active(true)
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(characterType, "id", 1L);

        UserCharacter character = UserCharacter.builder()
                .userId(userId)
                .characterType(characterType)
                .name("Nova")
                .level(1)
                .exp(0)
                .fullness(50)
                .energy(50)
                .affection(50)
                .active(true)
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }
}
