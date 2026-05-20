package p5laris.ai.domain.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
                JSON key는 characterMessage, completionQuestion, completionCharacterResponse 세 개만 사용한다.
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
