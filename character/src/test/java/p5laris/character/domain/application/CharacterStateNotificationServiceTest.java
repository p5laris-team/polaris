package p5laris.character.domain.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.character.domain.domain.entity.CharacterType;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.infrastructure.grpc.NotificationPushClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterStateNotificationServiceTest {

    @Mock
    private UserCharacterRepository userCharacterRepository;

    @Mock
    private NotificationPushClient notificationPushClient;

    private CharacterStateNotificationService service;

    @BeforeEach
    void setUp() {
        service = new CharacterStateNotificationService(userCharacterRepository, notificationPushClient);
    }

    @Test
    void 상태가_감소해_배고픔이_되면_상태_알림을_요청한다() {
        UserCharacter character = character(10L, 1L, "무무", 45, 80, 80);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(6 * 3600));
        when(userCharacterRepository.findByActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of(character)));

        int requestedCount = service.dispatchDueStateNotifications(100);

        assertThat(requestedCount).isEqualTo(1);
        verify(notificationPushClient).sendCharacterStateNotification(
                1L,
                10L,
                "무무",
                CharacterMood.HUNGRY
        );
    }

    @Test
    void 상태가_감소해도_정상_범위면_알림을_보내지_않는다() {
        UserCharacter character = character(10L, 1L, "무무", 90, 90, 90);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(6 * 3600));
        when(userCharacterRepository.findByActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of(character)));

        int requestedCount = service.dispatchDueStateNotifications(100);

        assertThat(requestedCount).isZero();
        verify(notificationPushClient, never()).sendCharacterStateNotification(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void 상태_감소_주기가_아직_아니면_알림을_보내지_않는다() {
        UserCharacter character = character(10L, 1L, "무무", 30, 80, 80);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(2 * 3600));
        when(userCharacterRepository.findByActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of(character)));

        int requestedCount = service.dispatchDueStateNotifications(100);

        assertThat(requestedCount).isZero();
        verify(notificationPushClient, never()).sendCharacterStateNotification(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void 알림_요청이_실패해도_스케줄러_흐름은_깨지지_않는다() {
        UserCharacter character = character(10L, 1L, "무무", 45, 80, 80);
        ReflectionTestUtils.setField(character, "lastStatDecreasedAt", Instant.now().minusSeconds(6 * 3600));
        when(userCharacterRepository.findByActiveTrue(any()))
                .thenReturn(new PageImpl<>(List.of(character)));
        doThrow(new RuntimeException("notification unavailable"))
                .when(notificationPushClient)
                .sendCharacterStateNotification(1L, 10L, "무무", CharacterMood.HUNGRY);

        assertThatCode(() -> service.dispatchDueStateNotifications(100))
                .doesNotThrowAnyException();
    }

    private UserCharacter character(
            Long characterId,
            Long userId,
            String name,
            int fullness,
            int energy,
            int affection
    ) {
        CharacterType characterType = CharacterType.builder()
                .code("MUMU")
                .name("무무")
                .active(true)
                .build();
        ReflectionTestUtils.setField(characterType, "id", 1L);

        UserCharacter character = UserCharacter.builder()
                .userId(userId)
                .characterType(characterType)
                .name(name)
                .level(1)
                .exp(0)
                .fullness(fullness)
                .energy(energy)
                .affection(affection)
                .active(true)
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }
}
