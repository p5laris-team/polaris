package p5laris.ai.domain.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.exception.FallbackRequiredException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiMissionTextGeneratorTest {

    @Test
    void Gemini_JSON_응답을_미션_문구_candidate로_변환한다() {
        GeminiMissionTextGenerator generator = new GeminiMissionTextGenerator(
                new StubAiChatClient("""
                        {
                          "title": "물 한 컵 마시기",
                          "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                          "characterMessage": "무... 무무... (해석: 물 한 컵 마셔봐요.)",
                          "completionQuestion": "마시고 나서 어땠어?",
                          "completionCharacterResponse": "잘했어. 작은 시작을 기억할게.",
                          "category": "BASIC_ROUTINE",
                          "difficulty": "EASY"
                        }
                        """),
                new ObjectMapper()
        );

        MissionTextCandidate candidate = generator.generate(validCommand());

        assertThat(candidate.title()).isEqualTo("물 한 컵 마시기");
        assertThat(candidate.description()).contains("물 한 컵");
        assertThat(candidate.characterMessage()).contains("무... 무무...");
        assertThat(candidate.completionQuestion()).isEqualTo("마시고 나서 어땠어?");
        assertThat(candidate.completionCharacterResponse()).contains("작은 시작");
        assertThat(candidate.category()).isEqualTo("BASIC_ROUTINE");
        assertThat(candidate.difficulty()).isEqualTo("EASY");
    }

    @Test
    void 코드블록으로_감싼_JSON_응답도_해석한다() {
        GeminiMissionTextGenerator generator = new GeminiMissionTextGenerator(
                new StubAiChatClient("""
                        ```json
                        {
                          "title": "물 한 컵 마시기",
                          "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                          "characterMessage": "천천히 물 한 컵 마셔보자.",
                          "completionQuestion": "몸이 조금 편해졌어?",
                          "completionCharacterResponse": "오늘의 수분 보충을 기억할게.",
                          "category": "BASIC_ROUTINE",
                          "difficulty": "EASY"
                        }
                        ```
                        """),
                new ObjectMapper()
        );

        MissionTextCandidate candidate = generator.generate(validCommand());

        assertThat(candidate.characterMessage()).isEqualTo("천천히 물 한 컵 마셔보자.");
    }

    @Test
    void Gemini_응답이_JSON이_아니면_fallback_대상으로_분류한다() {
        GeminiMissionTextGenerator generator = new GeminiMissionTextGenerator(
                new StubAiChatClient("미안하지만 JSON은 안 줄래요."),
                new ObjectMapper()
        );

        assertThatThrownBy(() -> generator.generate(validCommand()))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.INVALID_OUTPUT);
    }

    @Test
    void Gemini_호출_timeout은_TIMEOUT으로_분류한다() {
        GeminiMissionTextGenerator generator = new GeminiMissionTextGenerator(
                (systemPrompt, userPrompt) -> {
                    throw new RuntimeException("request timeout");
                },
                new ObjectMapper()
        );

        assertThatThrownBy(() -> generator.generate(validCommand()))
                .isInstanceOf(FallbackRequiredException.class)
                .extracting("errorType")
                .isEqualTo(AiErrorType.TIMEOUT);
    }

    @Test
    void Gemini_prompt는_무무_발화_패턴을_고정하지_않고_섞도록_요청한다() {
        CapturingAiChatClient chatClient = new CapturingAiChatClient("""
                {
                  "title": "물 한 컵 마시기",
                  "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                  "characterMessage": "무우... 무...? (해석: 물 한 컵 마셔봐요.)",
                  "completionQuestion": "무...? (해석: 마시고 나서 어땠나요?)",
                  "completionCharacterResponse": "무...! (해석: 작은 완료도 충분히 반짝였어요.)",
                  "category": "BASIC_ROUTINE",
                  "difficulty": "EASY"
                }
                """);
        GeminiMissionTextGenerator generator = new GeminiMissionTextGenerator(chatClient, new ObjectMapper());

        generator.generate(validCommand());

        assertThat(chatClient.systemPrompt)
                .contains("\"무\"", "\"무무\"", "\"무우\"", "\"무...?\"", "\"무...!\"")
                .contains("title과 description은 사용자가 바로 읽는 일반 미션 문장")
                .contains("캐릭터 말투는 characterMessage, completionQuestion, completionCharacterResponse 세 필드에만 적용")
                .contains("missionIntensity를 목표 난이도로 맞추는 것을 원칙")
                .contains("운동/움직임 미션의 NORMAL")
                .contains("missionIntensity가 CHALLENGE")
                .contains("currentTimeSlot과 timeSlotPolicy를 반드시 따른다")
                .contains("timeSlotPolicy.blockedCategories에 포함된 category는 절대 선택하지 않는다")
                .contains("currentTimeSlot이 NIGHT 또는 LATE_NIGHT이면 햇빛")
                .contains("currentTimeSlot이 LATE_NIGHT이면 연락, 메시지, 전화")
                .contains("항상 \"(해석: ...)\"")
                .contains("괄호 밖에는 \"무\", \"우\", 공백, \".\", \"?\", \"!\", \"…\"만 쓴다")
                .contains("예시에 나온 행동이나 문장을 실제 미션 후보로 재사용하지 않는다")
                .contains("MUMU 나쁜 형식: \"무우... 미션 내용을 괄호 밖에 쓰는 무우...?\"")
                .contains("잘못된 title 형식: \"무우... 무...? (해석: 일반 미션 제목)\"")
                .contains("MUMU 좋은 형식: \"무우... 무...? (해석: 실제 제안 문장)\"")
                .contains("별조각은 서비스의 보상 화폐")
                .contains("별조각이라는 단어는 금지")
                .doesNotContain("반드시 \"무... 무무...\"");
    }

    private MissionTextGenerationCommand validCommand() {
        return new MissionTextGenerationCommand(
                1001L,
                2001L,
                "MUMU",
                3001L,
                "물 한 컵 마시기",
                "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
                "BASIC_ROUTINE",
                "EASY",
                "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
                "물 마시고 나서 기분이 조금 달라졌어?",
                "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
                "{\"routineGoal\":\"WAKE_UP\"}",
                "{\"recentRejected\":[]}",
                UUID.randomUUID().toString()
        );
    }

    private record StubAiChatClient(String content) implements AiChatClient {

        @Override
        public String call(String systemPrompt, String userPrompt) {
            return content;
        }
    }

    private static class CapturingAiChatClient implements AiChatClient {
        private final String content;
        private String systemPrompt;

        private CapturingAiChatClient(String content) {
            this.content = content;
        }

        @Override
        public String call(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            return content;
        }
    }
}
