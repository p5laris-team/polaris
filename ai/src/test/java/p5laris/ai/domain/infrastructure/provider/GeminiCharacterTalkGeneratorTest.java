package p5laris.ai.domain.infrastructure.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import p5laris.ai.domain.application.dto.CharacterTalkGenerationCommand;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.infrastructure.config.AiCharacterTalkProperties;
import p5laris.ai.domain.infrastructure.tool.CharacterTalkToolsFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiCharacterTalkGeneratorTest {

    @Test
    @DisplayName("별친구 대화 - Tool 객체를 provider streaming 호출에 함께 전달한다")
    void stream_passesToolsToProviderCall() {
        CapturingAiChatClient chatClient = new CapturingAiChatClient("무무 ㅠㅠ... (해석: 나 여기 있어. 오늘 말이 조금 엉켜도 천천히 들려줘.)");
        AiCharacterTalkProperties properties = new AiCharacterTalkProperties();
        Object toolObject = new TestTool();
        CharacterTalkToolsFactory toolsFactory = mock(CharacterTalkToolsFactory.class);
        CharacterTalkGenerationCommand command = command();
        when(toolsFactory.create(command)).thenReturn(toolObject);

        GeminiCharacterTalkGenerator generator = new GeminiCharacterTalkGenerator(
                chatClient,
                properties,
                toolsFactory
        );

        List<String> chunks = new ArrayList<>();
        generator.stream(command, chunks::add);

        assertEquals("무무 ㅠㅠ... (해석: 나 여기 있어. 오늘 말이 조금 엉켜도 천천히 들려줘.)", String.join("", chunks));
        assertSame(toolObject, chatClient.tools[0]);
        assertTrue(chatClient.systemPrompt.contains("필요할 때만 Tool을 호출"));
        assertTrue(chatClient.systemPrompt.contains("단순 인사, 잡담, 감정 표현, 하소연에는 Tool 호출 없이"));
        assertTrue(chatClient.systemPrompt.contains("통역 설명문이 아니라 캐릭터가 사용자에게 직접 건네는 말"));
        assertTrue(chatClient.systemPrompt.contains("간접화법은 금지"));
        assertTrue(chatClient.systemPrompt.contains("캐릭터 이름\" 값은 별친구 자신의 이름"));
        assertTrue(chatClient.systemPrompt.contains("모든 MUMU 답변을 \"무... 무무\"로 시작하지 않는다"));
        assertTrue(chatClient.systemPrompt.contains("나 칭찬받은 거야?"));
        assertTrue(chatClient.systemPrompt.contains("그 말 좀 더 해줘도 돼"));
        assertTrue(chatClient.userPrompt.contains("사용자 이름으로 착각하지 않는다"));
        assertTrue(chatClient.userPrompt.contains("fallbackContext JSON"));
        assertTrue(chatClient.userPrompt.contains("conversationHistory JSON"));
        assertTrue(chatClient.userPrompt.contains("longTermMemoryContext JSON"));
        assertTrue(chatClient.systemPrompt.contains("기억하고 있다"));
        assertTrue(chatClient.systemPrompt.contains("기억한다고 거짓말하지 않는다"));
    }

    private CharacterTalkGenerationCommand command() {
        return new CharacterTalkGenerationCommand(
                1L,
                1L,
                "MUMU",
                "무다리",
                "오늘 좀 피곤해",
                "TAP",
                "{\"fallback\":true}",
                "request-1"
        );
    }

    private static class CapturingAiChatClient implements AiChatClient {

        private final String content;
        private String systemPrompt;
        private String userPrompt;
        private Object[] tools;

        private CapturingAiChatClient(String content) {
            this.content = content;
        }

        @Override
        public String call(String systemPrompt, String userPrompt) {
            return content;
        }

        @Override
        public Flux<String> streamPlainTextWithTools(String systemPrompt, String userPrompt, Object... tools) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            this.tools = tools;
            return Flux.just(content);
        }
    }

    private static class TestTool {
    }
}
