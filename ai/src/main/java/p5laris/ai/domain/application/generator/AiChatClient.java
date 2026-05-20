package p5laris.ai.domain.application.generator;

/**
 * Spring AI ChatClient를 직접 application service에 노출하지 않기 위한 작은 포트다.
 *
 * 테스트에서는 이 포트를 fake로 바꿔 실제 Gemini API key 없이도 provider 동작을 검증한다.
 */
public interface AiChatClient {

    String call(String systemPrompt, String userPrompt);
}
