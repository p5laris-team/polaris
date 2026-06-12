package p5laris.ai.domain.application.prompt;

public record RenderedPrompt(
        String systemPrompt,
        String userPrompt
) {

    public RenderedPrompt {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        userPrompt = userPrompt == null ? "" : userPrompt;
    }
}
