package p5laris.ai.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.CharacterTalkStreamMetadata;
import p5laris.ai.domain.application.dto.PreparedCharacterTalkContext;
import p5laris.ai.domain.application.generator.AiRateLimiter;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.application.generator.CharacterTalkStreamEmitter;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.domain.policy.CharacterTalkValidationPolicy;
import p5laris.ai.domain.infrastructure.config.AiCircuitBreakerProperties;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;
import p5laris.ai.domain.infrastructure.provider.AiProviderCircuitBreaker;
import p5laris.ai.domain.infrastructure.provider.GeminiCharacterTalkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCharacterTalkServiceTest {

    @Test
    @DisplayName("별친구 대화 - 외부 provider가 꺼져 있으면 캐릭터별 fallback을 반환")
    void streamCharacterTalk_providerDisabled_returnsFallback() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(false, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        service.streamCharacterTalk(command("MUMU", "무무야 안녕"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertEquals(AiErrorType.PROVIDER_ERROR, emitter.errorType);
        assertTrue(emitter.joinedText().startsWith("무"));
        assertTrue(emitter.joinedText().contains("(해석:"));
        verify(generator, never()).stream(any(), any());
        verify(rateLimiter, never()).checkAllowed(
                anyLong(),
                any(AiProviderType.class),
                anyString()
        );
    }

    @Test
    @DisplayName("별친구 대화 - 사용자 입력이 너무 길면 provider 호출 없이 fallback을 반환")
    void streamCharacterTalk_tooLongMessage_returnsFallback() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(true, 3, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        service.streamCharacterTalk(command("NOVA", "너무 긴 메시지"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertEquals(AiErrorType.INVALID_OUTPUT, emitter.errorType);
        assertTrue(emitter.joinedText().contains("천천히 같이 짚어볼게"));
        verify(generator, never()).stream(any(), any());
        verify(rateLimiter, never()).checkAllowed(
                anyLong(),
                any(AiProviderType.class),
                anyString()
        );
    }

    @Test
    @DisplayName("별친구 대화 - 무무 fallback도 사용자 감정에 맞는 발화 리듬을 고른다")
    void streamCharacterTalk_mumuFallback_usesEmotionalUtterance() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(false, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        service.streamCharacterTalk(command("MUMU", "무다리 칭찬해!"), emitter);

        assertTrue(emitter.fallbackUsed);
        assertTrue(emitter.joinedText().startsWith("무"));
        assertTrue(emitter.joinedText().contains("(해석:"));
        assertTrue(emitter.joinedText().contains("칭찬")
                || emitter.joinedText().contains("좋")
                || emitter.joinedText().contains("기뻐")
                || emitter.joinedText().contains("신났"));
    }

    @Test
    @DisplayName("별친구 대화 - provider chunk를 순서대로 흘리고 DONE으로 닫는다")
    void streamCharacterTalk_providerChunks_areForwarded() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(true, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("작은 별빛 하나만 ");
            consumer.accept("챙겨도 괜찮아.");
            return AiTokenUsage.empty();
        }).when(generator).stream(any(), any());

        service.streamCharacterTalk(command("NOVA", "오늘 좀 피곤해"), emitter);

        assertEquals("작은 별빛 하나만 챙겨도 괜찮아.", emitter.joinedText());
        assertTrue(emitter.completed);
        assertTrue(!emitter.fallbackUsed);
        verify(rateLimiter).checkAllowed(anyLong(), any(AiProviderType.class), anyString());
    }

    @Test
    @DisplayName("별친구 대화 - 무무도 안전한 발화 prefix는 먼저 스트리밍한다")
    void streamCharacterTalk_mumuStreamsSafeUtterancePrefix() {
        GeminiCharacterTalkGenerator generator = mock(GeminiCharacterTalkGenerator.class);
        AiRateLimiter rateLimiter = mock(AiRateLimiter.class);
        AiCharacterTalkService service = service(true, 300, generator, rateLimiter);
        CapturingEmitter emitter = new CapturingEmitter();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("무무 ㅠㅠ... ");
            assertEquals(List.of("무무 ㅠㅠ... "), emitter.chunks);
            consumer.accept("(해");
            assertEquals(List.of("무무 ㅠㅠ... "), emitter.chunks);
            consumer.accept("석: 나 여기 있어. 지금 말이 조금 엉켜도 천천히 들려줘.)");
            return AiTokenUsage.empty();
        }).when(generator).stream(any(), any());

        service.streamCharacterTalk(command("MUMU", "오늘 좀 피곤해"), emitter);

        assertEquals("무무 ㅠㅠ... (해석: 나 여기 있어. 지금 말이 조금 엉켜도 천천히 들려줘.)", emitter.joinedText());
        assertTrue(emitter.completed);
        assertTrue(!emitter.fallbackUsed);
    }

    private AiCharacterTalkService service(
            boolean providerEnabled,
            int maxUserMessageLength,
            GeminiCharacterTalkGenerator generator,
            AiRateLimiter rateLimiter
    ) {
        AiProviderProperties providerProperties = new AiProviderProperties();
        providerProperties.setEnabled(providerEnabled);
        providerProperties.setType("gemini");
        providerProperties.setModel("gemini-2.5-flash");

        AiCharacterTalkProperties talkProperties = new AiCharacterTalkProperties();
        talkProperties.setMaxUserMessageLength(maxUserMessageLength);
        talkProperties.setMaxReplyLength(350);
        CharacterTalkHistoryService historyService = mock(CharacterTalkHistoryService.class);
        when(historyService.prepare(any())).thenReturn(preparedContext());

        return new AiCharacterTalkService(
                providerProperties,
                rateLimiter,
                new AiProviderCircuitBreaker(new AiCircuitBreakerProperties()),
                generator,
                new CharacterTalkValidationPolicy(talkProperties),
                historyService
        );
    }

    private PreparedCharacterTalkContext preparedContext() {
        return new PreparedCharacterTalkContext(
                mock(CharacterTalkSession.class),
                "session-1",
                true,
                LocalDateTime.now().plusMinutes(30),
                "[]",
                "[]",
                6,
                3,
                0
        );
    }

    private CharacterTalkGenerationCommand command(String characterType, String userMessage) {
        return new CharacterTalkGenerationCommand(
                1L,
                1L,
                characterType,
                "MUMU".equals(characterType) ? "무다리" : characterType,
                userMessage,
                "TAP",
                "{}",
                "request-1"
        );
    }

    private static class CapturingEmitter implements CharacterTalkStreamEmitter {
        private final List<String> chunks = new ArrayList<>();
        private boolean completed;
        private boolean fallbackUsed;
        private AiErrorType errorType;
        private CharacterTalkStreamMetadata metadata;

        @Override
        public void emitMeta(CharacterTalkStreamMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void emitDelta(String text) {
            chunks.add(text);
        }

        @Override
        public void complete(boolean fallbackUsed, AiErrorType errorType, AiTokenUsage tokenUsage) {
            this.completed = true;
            this.fallbackUsed = fallbackUsed;
            this.errorType = errorType;
        }

        @Override
        public boolean hasEmittedDelta() {
            return !chunks.isEmpty();
        }

        private String joinedText() {
            return String.join("", chunks);
        }
    }
}
