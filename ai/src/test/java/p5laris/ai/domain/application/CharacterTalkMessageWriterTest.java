package p5laris.ai.domain.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiTokenUsage;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;
import p5laris.ai.domain.domain.repository.CharacterTalkMessageRepository;
import p5laris.ai.domain.domain.repository.CharacterTalkSessionRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterTalkMessageWriterTest {

    @Test
    @DisplayName("별친구 대화 저장 - 세션 락을 잡은 뒤 순번을 증가시켜 저장한다")
    void recordMessages_usesLockedSessionSequence() {
        CharacterTalkSessionRepository sessionRepository = mock(CharacterTalkSessionRepository.class);
        CharacterTalkMessageRepository messageRepository = mock(CharacterTalkMessageRepository.class);
        CharacterTalkMessageWriter writer = new CharacterTalkMessageWriter(sessionRepository, messageRepository);
        CharacterTalkSession session = CharacterTalkSession.create(
                "session-1",
                1L,
                10L,
                "MUMU",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30)
        );
        when(sessionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(session));

        CharacterTalkGenerationCommand command = new CharacterTalkGenerationCommand(
                1L,
                10L,
                "MUMU",
                "무무",
                "오늘 회사 다녀와서 너무 힘들었어",
                "TAP",
                "{}",
                "request-1"
        );

        writer.recordUserMessage(1L, command, LocalDateTime.now(), LocalDateTime.now().plusMinutes(30));
        writer.recordAssistantResponse(1L, command, "무... 무무. (해석: 기억해요.)", false,
                new AiTokenUsage(11, 7, 18));

        ArgumentCaptor<CharacterTalkMessage> captor = ArgumentCaptor.forClass(CharacterTalkMessage.class);
        verify(sessionRepository, times(2)).findByIdForUpdate(1L);
        verify(messageRepository, times(2)).save(captor.capture());

        CharacterTalkMessage userMessage = captor.getAllValues().get(0);
        CharacterTalkMessage assistantMessage = captor.getAllValues().get(1);
        assertEquals(CharacterTalkMessageRole.USER, userMessage.getRole());
        assertEquals(1, userMessage.getSequence());
        assertEquals(CharacterTalkMessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(2, assistantMessage.getSequence());
        assertEquals(2, session.getMessageCount());
        assertEquals(18, session.getTotalActualTokens());
    }
}
