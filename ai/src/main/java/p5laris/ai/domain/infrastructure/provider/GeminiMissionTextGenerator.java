package p5laris.ai.domain.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import p5laris.ai.domain.application.dto.MissionTextCandidate;
import p5laris.ai.domain.application.dto.MissionTextGenerationCommand;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.application.generator.ExternalMissionTextGenerator;
import p5laris.ai.domain.domain.enums.AiErrorType;
import p5laris.ai.domain.domain.enums.AiProviderType;
import p5laris.ai.domain.exception.FallbackRequiredException;

/**
 * Gemini에게 캐릭터 말투 기반 미션 문구 생성을 요청하는 provider 구현체다.
 *
 * Gemini는 문구 3개만 만들고, 미션 제목/설명/카테고리/보상 같은 비즈니스 의미는 바꾸지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiMissionTextGenerator implements ExternalMissionTextGenerator {

    private final AiChatClient aiChatClient;
    private final ObjectMapper objectMapper;

    @Override
    public AiProviderType providerType() {
        return AiProviderType.GEMINI;
    }

    @Override
    public MissionTextCandidate generate(MissionTextGenerationCommand command) {
        try {
            String content = aiChatClient.call(systemPrompt(), userPrompt(command));
            return parseCandidate(content);
        } catch (FallbackRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini provider 호출 실패. 예외클래스={}, 메시지={}",
                    e.getClass().getSimpleName(), e.getMessage());
            throw new FallbackRequiredException(toErrorType(e), "Gemini 문구 생성에 실패했습니다.");
        }
    }

    private MissionTextCandidate parseCandidate(String content) {
        if (content == null || content.isBlank()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "Gemini 응답이 비어 있습니다.");
        }

        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            return new MissionTextCandidate(
                    requiredText(root, "characterMessage"),
                    requiredText(root, "completionQuestion"),
                    requiredText(root, "completionCharacterResponse")
            );
        } catch (FallbackRequiredException e) {
            throw e;
        } catch (Exception e) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "Gemini 응답 JSON을 해석할 수 없습니다.");
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.asText().isBlank()) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "Gemini 응답 필드가 비어 있습니다.");
        }
        return field.asText();
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new FallbackRequiredException(AiErrorType.INVALID_OUTPUT, "Gemini 응답에 JSON 객체가 없습니다.");
        }
        return trimmed.substring(start, end + 1);
    }

    private AiErrorType toErrorType(Exception e) {
        String name = e.getClass().getSimpleName().toLowerCase();
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (name.contains("timeout") || message.contains("timeout") || message.contains("timed out")) {
            return AiErrorType.TIMEOUT;
        }
        return AiErrorType.PROVIDER_ERROR;
    }

    private String systemPrompt() {
        return """
                너는 Polaris 서비스의 캐릭터 말투 변환기다.
                사용자가 수행할 미션의 의미는 절대 바꾸지 말고, 사용자에게 보여줄 문구만 캐릭터 말투로 바꾼다.
                반드시 JSON 객체 하나만 반환한다.
                반환 JSON은 줄바꿈 없는 한 줄 compact JSON으로 작성한다.
                JSON key는 characterMessage, completionQuestion, completionCharacterResponse 세 개만 사용한다.
                JSON 앞뒤에 설명, 마크다운 코드블록, 주석을 붙이지 않는다.
                문자열 value 내부에도 줄바꿈을 넣지 않는다.
                각 value는 한국어 100자 이하로 작성한다.
                사용자 이름, 닉네임, [user], {user}, placeholder 표현은 절대 쓰지 않는다.
                미션 설명에 없는 시간대, 장소, 감정 상태를 단정하지 않는다.
                온보딩 context와 최근 미션 context는 말투와 난이도 조절에만 참고하고, 미션 의미를 새로 만들지 않는다.
                NOVA는 느리고 조심스럽고 다정한 별알 말투로 말하며, 작은 빛/별조각 같은 부드러운 이미지를 짧게 쓸 수 있다.
                MUMU는 "무", "무무", "무우", "무...?", "무...!" 같은 짧은 발화를 섞고, 항상 "(해석: ...)"을 함께 쓴다.
                MUMU의 각 value는 무무 발화 한 덩어리와 "(해석: ...)" 한 번으로 끝내고, 같은 발화를 세 value에 반복하지 않는다.
                MUMU는 괄호 밖에 미션 제목이나 한국어 의미 문장을 절대 쓰지 않는다. 괄호 밖에는 "무", "우", 공백, ".", "?", "!", "…"만 쓴다.
                MUMU 나쁜 예: "무우... 오래된 알림 하나 지우는 무우...?"처럼 괄호 밖에 미션 내용을 섞으면 안 된다.
                MUMU 좋은 예: "무우... 무...? (해석: 오래된 알림 하나를 지워볼까요?)"
                MUMU의 completionQuestion도 무무 발화로 시작하되 해석은 사용자가 짧게 답할 수 있는 질문형으로 쓴다.
                JJORY는 건조하고 짧은 반말 농담 말투를 사용한다. 존댓말보다 "~임", "~됨", "인정" 같은 짧은 표현을 선호한다.
                JJORY도 무례하거나 사용자를 비난하면 안 된다.
                비난, 죄책감 유발, 낙인, 협박, 과도한 자기비하 표현은 쓰지 않는다.
                미션 제목, 카테고리, 난이도, 보상은 새로 만들거나 바꾸지 않는다.
                completionQuestion은 사용자가 미션을 수행한 뒤 짧게 답할 수 있는 질문이어야 한다.
                """;
    }

    private String userPrompt(MissionTextGenerationCommand command) {
        return """
                캐릭터 타입: %s
                미션 제목: %s
                미션 설명: %s
                카테고리: %s
                난이도: %s

                fallback 제안 문구: %s
                fallback 완료 질문: %s
                fallback 완료 반응: %s

                온보딩 context JSON:
                %s

                최근 미션 context JSON:
                %s

                반환 형식:
                {
                  "characterMessage": "캐릭터가 사용자에게 미션을 제안하는 한두 문장",
                  "completionQuestion": "사용자가 완료 후 답할 짧은 질문",
                  "completionCharacterResponse": "완료 후 캐릭터가 보여줄 따뜻한 반응"
                }
                """.formatted(
                command.characterType(),
                command.baseTitle(),
                command.baseDescription(),
                command.category(),
                command.difficulty(),
                command.fallbackCharacterMessage(),
                command.fallbackQuestion(),
                command.fallbackCompletionResponse(),
                command.onboardingContextJson(),
                command.recentMissionContextJson()
        );
    }
}
