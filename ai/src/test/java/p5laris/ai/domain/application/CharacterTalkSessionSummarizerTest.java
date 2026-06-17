package p5laris.ai.domain.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.memory.CharacterTalkSessionSummary;
import p5laris.ai.domain.application.prompt.PromptTemplateService;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterTalkSessionSummarizerTest {

    private static final PromptTemplateService FALLBACK_PROMPTS = (category, variables, fallback) -> fallback;

    @Test
    void ai_json_응답에서_context와_diary를_분리한다() {
        CharacterTalkSessionSummarizer summarizer = new CharacterTalkSessionSummarizer(
                (systemPrompt, userPrompt) -> """
                        ```json
                        {"contextSummary":"사용자는 산책을 기억하고 싶어 했다.","diaryText":"오늘 나는 별친구와 산책 이야기를 나눴다."}
                        ```
                        """,
                new ObjectMapper(),
                FALLBACK_PROMPTS
        );

        CharacterTalkSessionSummary summary = summarizer.summarize(
                session(),
                List.of(message(CharacterTalkMessageRole.USER, "오늘 산책한 게 좋았어", 1))
        );

        assertThat(summary.contextSummary()).isEqualTo("사용자는 산책을 기억하고 싶어 했다.");
        assertThat(summary.diaryText()).isEqualTo("오늘 나는 별친구와 산책 이야기를 나눴다.");
    }

    @Test
    void ai_요약이_실패하면_규칙기반_일기와_context를_반환한다() {
        CharacterTalkSessionSummarizer summarizer = new CharacterTalkSessionSummarizer(
                (systemPrompt, userPrompt) -> {
                    throw new IllegalStateException("provider failed");
                },
                new ObjectMapper(),
                FALLBACK_PROMPTS
        );

        CharacterTalkSessionSummary summary = summarizer.summarize(
                session(),
                List.of(
                        message(CharacterTalkMessageRole.USER, "오늘 너무 힘들었어", 1),
                        message(CharacterTalkMessageRole.ASSISTANT, "무... 무무. (해석: 쉬어도 괜찮아요.)", 2)
                )
        );

        assertThat(summary.contextSummary()).contains("사용자: 오늘 너무 힘들었어");
        assertThat(summary.diaryText()).contains("오늘 나는 별친구와");
        assertThat(summary.diaryText()).contains("쉬어도 괜찮아요.");
    }

    private CharacterTalkSession session() {
        return CharacterTalkSession.create(
                "session-1",
                1L,
                10L,
                "MUMU",
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(30)
        );
    }

    private CharacterTalkMessage message(CharacterTalkMessageRole role, String content, int sequence) {
        return CharacterTalkMessage.create(
                session(),
                role,
                content,
                sequence,
                "request-" + sequence,
                false
        );
    }
}
