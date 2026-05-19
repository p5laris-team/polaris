package p5laris.ai.domain.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI provider 설정값을 application.yaml/env에서 읽어오는 클래스다.
 *
 * 이번 PR에서는 local generator만 사용하지만,
 * 다음 PR에서 type=gemini/openai 같은 provider 선택값을 이 클래스가 들고 있게 된다.
 */
@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {

    private boolean enabled = false;
    private String type = "local";
    private String model = "local-tone-v1";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    // model 값이 비어 있으면 로그 분석에서 구분 가능한 local 기본 모델명으로 보정한다.
    public String resolvedModel() {
        if (model == null || model.isBlank()) {
            return "local-tone-v1";
        }
        return model.trim();
    }
}
