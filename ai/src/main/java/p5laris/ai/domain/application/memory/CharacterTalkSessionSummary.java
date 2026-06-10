package p5laris.ai.domain.application.memory;

/**
 * 별친구 대화 세션을 두 용도로 나눈 요약 결과다.
 *
 * contextSummary는 다음 대화의 장기 기억 검색/주입에 쓰고, diaryText는 사용자에게 일기처럼 보여 준다.
 */
public record CharacterTalkSessionSummary(
        String contextSummary,
        String diaryText
) {

    public boolean isBlank() {
        return isBlank(contextSummary) && isBlank(diaryText);
    }

    public String resolvedContextSummary() {
        return isBlank(contextSummary) ? diaryText : contextSummary;
    }

    public String resolvedDiaryText() {
        return isBlank(diaryText) ? contextSummary : diaryText;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
