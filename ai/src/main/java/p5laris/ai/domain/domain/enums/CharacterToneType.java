package p5laris.ai.domain.domain.enums;

/**
 * MVP 캐릭터 3종의 말투 규칙이다.
 *
 * 외부 AI가 붙기 전에는 이 enum이 local fallback 역할을 한다.
 * 외부 AI가 붙은 뒤에도 provider 실패 시 안전하게 사용할 수 있는 기준 문구가 된다.
 */
public enum CharacterToneType {
    // 노바: 느리고 조심스럽고 다정한 별알 말투.
    NOVA {
        @Override
        public String characterMessage(String baseTitle) {
            return shorten(baseTitle, 28) + "... 천천히 해보자. 작은 빛 하나면 충분해.";
        }

        @Override
        public String completionQuestion() {
            return "끝나고 나서 마음이 조금 달라졌어?";
        }

        @Override
        public String completionResponse() {
            return "잘했어... 오늘의 작은 빛을 별조각으로 남겨둘게.";
        }
    },
    // 무무: 실제 발화는 "무..." 중심이고, 괄호 안에 시스템 해석을 함께 제공한다.
    MUMU {
        @Override
        public String characterMessage(String baseTitle) {
            return "무... 무무... (해석: " + shorten(baseTitle, 20) + " 해보자는 뜻이에요.)";
        }

        @Override
        public String completionQuestion() {
            return "무...? 무무... (해석: 해보고 나서 어땠나요?)";
        }

        @Override
        public String completionResponse() {
            return "무... 무무... (해석: 무무가 조용히 좋아하고 있어요.)";
        }
    },
    // 쪼리: 건조하고 짧게 농담하는 말투.
    JJORY {
        @Override
        public String characterMessage(String baseTitle) {
            return shorten(baseTitle, 28) + ". 이 정도면 작은 모험임. 반박은 안 받음.";
        }

        @Override
        public String completionQuestion() {
            return "해보니까 어땠음? 한 줄이면 됨.";
        }

        @Override
        public String completionResponse() {
            return "완료했네. 꽤 큰일임. 인정.";
        }
    };

    public abstract String characterMessage(String baseTitle);

    public abstract String completionQuestion();

    public abstract String completionResponse();

    // 제목이 너무 길면 말풍선 전체가 100자를 넘을 수 있으므로 앞부분만 사용한다.
    private static String shorten(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "작은 미션";
        }

        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
