package p5laris.ai.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.dto.TextEmbeddingCommand;
import p5laris.ai.domain.application.dto.TextEmbeddingResult;
import p5laris.ai.domain.application.memory.CharacterTalkSessionSummary;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;
import p5laris.ai.domain.domain.enums.CharacterTalkSessionStatus;
import p5laris.ai.domain.domain.repository.CharacterTalkMessageRepository;
import p5laris.ai.domain.domain.repository.CharacterTalkSessionRepository;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.config.AiEmbeddingProperties;
import p5laris.ai.domain.infrastructure.repository.CharacterTalkMemoryJdbcRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterTalkHistoryServiceTest {

    @Test
    void prepare는_만료세션을_context와_diary로_나눠_기억화한다() {
        CharacterTalkSessionRepository sessionRepository = mock(CharacterTalkSessionRepository.class);
        CharacterTalkMessageRepository messageRepository = mock(CharacterTalkMessageRepository.class);
        CharacterTalkMemoryJdbcRepository memoryJdbcRepository = mock(CharacterTalkMemoryJdbcRepository.class);
        CharacterTalkMessageWriter messageWriter = mock(CharacterTalkMessageWriter.class);
        CharacterTalkSessionSummarizer sessionSummarizer = mock(CharacterTalkSessionSummarizer.class);
        AiTextEmbeddingService textEmbeddingService = mock(AiTextEmbeddingService.class);
        AiCharacterTalkProperties talkProperties = new AiCharacterTalkProperties();
        talkProperties.setMemorySearchTopK(0);
        AiEmbeddingProperties embeddingProperties = embeddingProperties();
        CharacterTalkHistoryService service = new CharacterTalkHistoryService(
                sessionRepository,
                messageRepository,
                memoryJdbcRepository,
                messageWriter,
                sessionSummarizer,
                textEmbeddingService,
                talkProperties,
                embeddingProperties,
                new ObjectMapper()
        );

        CharacterTalkSession expiredSession = session("expired-session");
        ReflectionTestUtils.setField(expiredSession, "id", 100L);
        List<CharacterTalkMessage> messages = List.of(
                CharacterTalkMessage.create(
                        expiredSession,
                        CharacterTalkMessageRole.USER,
                        "오늘 산책한 게 좋았어",
                        1,
                        "request-1",
                        false
                )
        );

        when(sessionRepository.findTop10ByUserIdAndCharacterIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                eq(1L),
                eq(10L),
                eq(CharacterTalkSessionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(List.of(expiredSession));
        when(messageRepository.findBySessionIdOrderBySequenceAsc(100L)).thenReturn(messages);
        when(sessionSummarizer.summarize(eq(expiredSession), anyList()))
                .thenReturn(new CharacterTalkSessionSummary(
                        "사용자는 산책을 긍정적인 기억으로 남겼다.",
                        "오늘 나는 별친구와 산책 이야기를 나눴다."
                ));
        when(textEmbeddingService.generateTextEmbedding(any(TextEmbeddingCommand.class)))
                .thenReturn(new TextEmbeddingResult(
                        "gemini-embedding-001",
                        3,
                        List.of(1.0f, 0.0f, 0.0f),
                        "embedding-request"
                ));
        when(sessionRepository.save(any(CharacterTalkSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.findFirstByUserIdAndCharacterIdAndStatusAndExpiresAtAfterOrderByLastMessageAtDesc(
                eq(1L),
                eq(10L),
                eq(CharacterTalkSessionStatus.ACTIVE),
                any(LocalDateTime.class)
        )).thenReturn(Optional.empty());
        when(messageRepository.findRecentMessages(any(), any())).thenReturn(List.of());

        service.prepare(command());

        ArgumentCaptor<TextEmbeddingCommand> embeddingCaptor = ArgumentCaptor.forClass(TextEmbeddingCommand.class);
        verify(textEmbeddingService).generateTextEmbedding(embeddingCaptor.capture());
        assertThat(embeddingCaptor.getValue().text()).isEqualTo("사용자는 산책을 긍정적인 기억으로 남겼다.");

        verify(memoryJdbcRepository).upsertSessionSummary(
                eq(1L),
                eq(10L),
                eq(100L),
                eq("사용자는 산책을 긍정적인 기억으로 남겼다."),
                eq("오늘 나는 별친구와 산책 이야기를 나눴다."),
                eq("gemini-embedding-001"),
                eq(3),
                anyList()
        );
        assertThat(expiredSession.getStatus()).isEqualTo(CharacterTalkSessionStatus.MEMORY_READY);
    }

    private CharacterTalkSession session(String sessionId) {
        return CharacterTalkSession.create(
                sessionId,
                1L,
                10L,
                "MUMU",
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1)
        );
    }

    private CharacterTalkGenerationCommand command() {
        return new CharacterTalkGenerationCommand(
                1L,
                10L,
                "MUMU",
                "무무",
                "오늘도 기억해 줘",
                "TAP",
                "{}",
                "request-current"
        );
    }

    private AiEmbeddingProperties embeddingProperties() {
        AiEmbeddingProperties properties = new AiEmbeddingProperties();
        properties.setModel("gemini-embedding-001");
        properties.setDimension(3);
        return properties;
    }
}
