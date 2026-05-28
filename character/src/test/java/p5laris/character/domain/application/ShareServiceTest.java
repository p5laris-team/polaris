package p5laris.character.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.ShareCard;
import p5laris.character.domain.domain.entity.ShareLog;
import p5laris.character.domain.domain.entity.ShareRewardOutbox;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.repository.ShareCardRepository;
import p5laris.character.domain.domain.repository.ShareLogRepository;
import p5laris.character.domain.domain.repository.ShareRewardOutboxRepository;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;
import p5laris.character.domain.infrastructure.grpc.ShareRewardWalletClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ShareRewardOutboxRepository shareRewardOutboxRepository;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private ShareRewardWalletClient shareRewardWalletClient;

    @Mock
    private ShareRewardDispatcher shareRewardDispatcher;

    @Mock
    private TransactionTemplate transactionTemplate;

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

    @Test
    @DisplayName("createShareEvent credits wallet for today's first share reward")
    void createShareEvent_firstReward_creditsWallet() {
        runTransactionTemplateCallbacks();
        ShareCard card = createShareCard(800L, 1L, 10L);
        when(shareCardRepository.findById(800L)).thenReturn(Optional.of(card));
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(shareLogRepository.existsByUserIdAndShareDateAndRewardPaidTrue(anyLong(), any(LocalDate.class)))
                .thenReturn(false);
        when(shareLogRepository.saveAndFlush(any(ShareLog.class))).thenAnswer(invocation -> {
            ShareLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 900L);
            return log;
        });
        when(shareRewardOutboxRepository.saveAndFlush(any(ShareRewardOutbox.class))).thenAnswer(invocation -> {
            ShareRewardOutbox outbox = invocation.getArgument(0);
            ReflectionTestUtils.setField(outbox, "id", 950L);
            return outbox;
        });
        when(shareRewardDispatcher.dispatchNow(950L))
                .thenReturn(new ShareRewardWalletClient.WalletRewardResult(110, 700L));

        var result = shareService.createShareEvent(1L, 800L, "KAKAO", "LINK", "client-key");

        assertEquals(900L, result.shareEventId());
        assertTrue(result.rewardPaid());
        assertEquals(10, result.rewardStarPiece());
        assertEquals(110, result.walletStarPiece());
        verify(shareRewardOutboxRepository).saveAndFlush(any(ShareRewardOutbox.class));
        verify(shareRewardDispatcher).dispatchNow(950L);
        verify(shareRewardWalletClient, never()).getWalletStarPiece(anyLong());
    }

    @Test
    @DisplayName("createShareEvent replays existing daily reward without crediting wallet again")
    void createShareEvent_existingReward_replaysWithoutCredit() {
        runTransactionTemplateCallbacks();
        ShareCard card = createShareCard(800L, 1L, 10L);
        ShareLog existingLog = ShareLog.builder()
                .userId(1L)
                .characterId(10L)
                .shareCardId(800L)
                .shareType("LINK")
                .platform("KAKAO")
                .sharedAt(Instant.now())
                .shareDate(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
                .rewardStarPiece(10)
                .rewardPaid(true)
                .idempotencyKey("SHARE_REWARD:1:" + LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
                .build();
        ReflectionTestUtils.setField(existingLog, "id", 901L);

        when(shareCardRepository.findById(800L)).thenReturn(Optional.of(card));
        when(shareLogRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existingLog));
        when(shareRewardWalletClient.getWalletStarPiece(1L)).thenReturn(110);

        var result = shareService.createShareEvent(1L, 800L, "KAKAO", "LINK", "client-key");

        assertEquals(901L, result.shareEventId());
        assertTrue(result.rewardPaid());
        assertEquals(10, result.rewardStarPiece());
        assertEquals(110, result.walletStarPiece());
        verify(shareRewardWalletClient, never()).earnShareReward(anyLong(), anyLong(), anyInt(), anyString());
    }

    private void runTransactionTemplateCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
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

    private ShareCard createShareCard(Long shareCardId, Long userId, Long characterId) {
        ShareCard card = ShareCard.builder()
                .userId(userId)
                .characterId(characterId)
                .imageUrl("share-cards/" + userId + "/card.png")
                .headline("오늘도 반짝였어요.")
                .shareUrl("sh_abc12345")
                .build();
        ReflectionTestUtils.setField(card, "id", shareCardId);
        return card;
    }
}
