package p5laris.ai.domain.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import p5laris.ai.domain.application.generator.AiChatClient;
import p5laris.ai.domain.application.memory.CharacterTalkSessionSummary;
import p5laris.ai.domain.domain.entity.CharacterTalkMessage;
import p5laris.ai.domain.domain.entity.CharacterTalkSession;
import p5laris.ai.domain.domain.enums.CharacterTalkMessageRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterTalkSessionSummarizer {

    private static final int CONTEXT_SUMMARY_MAX_LENGTH = 700;
    private static final int DIARY_TEXT_MAX_LENGTH = 900;
    private static final int PROMPT_MESSAGE_LIMIT = 40;
    private static final int FALLBACK_MESSAGE_LIMIT = 12;

    private final AiChatClient aiChatClient;
    private final ObjectMapper objectMapper;

    public CharacterTalkSessionSummary summarize(
            CharacterTalkSession session,
            List<CharacterTalkMessage> messages
    ) {
        List<CharacterTalkMessage> orderedMessages = orderedMessages(messages);
        if (orderedMessages.isEmpty()) {
            return new CharacterTalkSessionSummary("", "");
        }

        CharacterTalkSessionSummary fallback = fallbackSummary(orderedMessages);
        try {
            String response = aiChatClient.call(systemPrompt(), userPrompt(session, orderedMessages));
            CharacterTalkSessionSummary aiSummary = parse(response);
            return new CharacterTalkSessionSummary(
                    choose(aiSummary.contextSummary(), fallback.contextSummary(), CONTEXT_SUMMARY_MAX_LENGTH),
                    choose(aiSummary.diaryText(), fallback.diaryText(), DIARY_TEXT_MAX_LENGTH)
            );
        } catch (Exception e) {
            log.warn("별친구 대화 세션 AI 요약 실패. sessionId={}, 예외클래스={}",
                    session.getSessionId(), e.getClass().getSimpleName());
            return fallback;
        }
    }

    private CharacterTalkSessionSummary parse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(extractJsonObject(response));
        return new CharacterTalkSessionSummary(
                trimForStorage(root.path("contextSummary").asText(""), CONTEXT_SUMMARY_MAX_LENGTH),
                trimForStorage(root.path("diaryText").asText(""), DIARY_TEXT_MAX_LENGTH)
        );
    }

    private String systemPrompt() {
        return """
                너는 별친구 대화 세션을 요약하는 백엔드 요약기다.
                반드시 JSON 객체만 반환한다.
                반환 필드는 contextSummary, diaryText 두 개만 사용한다.
                contextSummary는 다음 대화에서 참고할 사실 중심 한국어 요약이다. 일기체나 감정 과장은 피한다.
                diaryText는 사용자에게 보여 줄 1인칭 일기체 한국어 요약이다. 캐릭터 이름을 모르면 '별친구'라고 쓴다.
                대화에 없는 사실을 만들지 말고, 민감한 개인정보를 새로 추정하지 않는다.
                """;
    }

    private String userPrompt(CharacterTalkSession session, List<CharacterTalkMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("characterType: ").append(session.getCharacterType()).append('\n');
        builder.append("messages:\n");
        for (CharacterTalkMessage message : promptMessages(messages)) {
            builder
                    .append("- ")
                    .append(message.getSequence())
                    .append(". ")
                    .append(message.getRole() == CharacterTalkMessageRole.USER ? "사용자" : "별친구")
                    .append(": ")
                    .append(trimMessage(message.getContent(), 280))
                    .append('\n');
        }
        builder.append("""

                JSON 예시:
                {"contextSummary":"사용자는 최근 피로와 업무 부담을 이야기했고, 별친구는 휴식과 감정 정리를 권했다.","diaryText":"오늘 나는 별친구와 요즘 느끼는 피로에 대해 이야기했다. 별친구는 내가 잠깐 숨을 고를 수 있도록 차분히 곁을 지켜 주었다."}
                """);
        return builder.toString();
    }

    private CharacterTalkSessionSummary fallbackSummary(List<CharacterTalkMessage> messages) {
        String contextSummary = buildContextFallback(messages);
        String diaryText = buildDiaryFallback(messages);
        return new CharacterTalkSessionSummary(contextSummary, diaryText);
    }

    private String buildContextFallback(List<CharacterTalkMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("이전 대화 요약: ");
        messages.stream()
                .limit(FALLBACK_MESSAGE_LIMIT)
                .forEach(message -> builder
                        .append(message.getRole() == CharacterTalkMessageRole.USER ? "사용자: " : "별친구: ")
                        .append(trimMessage(message.getContent(), 120))
                        .append(' '));
        return trimForStorage(builder.toString(), CONTEXT_SUMMARY_MAX_LENGTH);
    }

    private String buildDiaryFallback(List<CharacterTalkMessage> messages) {
        List<String> userSnippets = snippets(messages, CharacterTalkMessageRole.USER, 3);
        List<String> assistantSnippets = snippets(messages, CharacterTalkMessageRole.ASSISTANT, 2);
        StringBuilder builder = new StringBuilder("오늘 나는 별친구와");
        if (userSnippets.isEmpty()) {
            builder.append(" 짧게 이야기를 나눴다.");
        } else {
            builder
                    .append(' ')
                    .append(String.join(", ", userSnippets))
                    .append("에 대해 이야기했다.");
        }
        if (!assistantSnippets.isEmpty()) {
            builder
                    .append(" 별친구는 ")
                    .append(String.join(", ", assistantSnippets))
                    .append("라고 답하며 대화를 이어 갔다.");
        }
        return trimForStorage(builder.toString(), DIARY_TEXT_MAX_LENGTH);
    }

    private List<String> snippets(
            List<CharacterTalkMessage> messages,
            CharacterTalkMessageRole role,
            int limit
    ) {
        return messages.stream()
                .filter(message -> message.getRole() == role)
                .map(message -> role == CharacterTalkMessageRole.ASSISTANT
                        ? extractAssistantMeaning(message.getContent())
                        : message.getContent())
                .map(value -> trimMessage(value, 80))
                .filter(value -> !value.isBlank())
                .limit(limit)
                .toList();
    }

    private String extractAssistantMeaning(String value) {
        String text = normalizeText(value);
        int start = text.indexOf("(해석:");
        if (start < 0) {
            return text;
        }
        int contentStart = start + "(해석:".length();
        int end = text.indexOf(')', contentStart);
        if (end <= contentStart) {
            return text;
        }
        return text.substring(contentStart, end);
    }

    private List<CharacterTalkMessage> promptMessages(List<CharacterTalkMessage> messages) {
        if (messages.size() <= PROMPT_MESSAGE_LIMIT) {
            return messages;
        }
        int edgeSize = PROMPT_MESSAGE_LIMIT / 2;
        List<CharacterTalkMessage> selected = new ArrayList<>(PROMPT_MESSAGE_LIMIT);
        selected.addAll(messages.subList(0, edgeSize));
        selected.addAll(messages.subList(messages.size() - edgeSize, messages.size()));
        return selected;
    }

    private List<CharacterTalkMessage> orderedMessages(List<CharacterTalkMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
                .sorted(Comparator.comparingInt(CharacterTalkMessage::getSequence))
                .toList();
    }

    private String choose(String value, String fallback, int maxLength) {
        String normalized = trimForStorage(value, maxLength);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return trimForStorage(fallback, maxLength);
    }

    private String trimMessage(String value, int maxLength) {
        return trimForStorage(value, maxLength);
    }

    private String trimForStorage(String value, int maxLength) {
        String normalized = normalizeText(value).replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractJsonObject(String value) {
        String text = normalizeText(value);
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return text;
        }
        return text.substring(start, end + 1);
    }
}
