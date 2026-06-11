package p5laris.mission.domain.application.memory;

import org.junit.jupiter.api.Test;
import p5laris.mission.domain.infrastructure.config.MissionMemoryEmbeddingProperties;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MissionMemoryEmbeddingBackoffPolicyTest {

    @Test
    void exponentialDelayIsCappedForRepeatedProviderFailures() {
        MissionMemoryEmbeddingProperties properties = new MissionMemoryEmbeddingProperties();
        properties.setRetryInitialDelaySeconds(15);
        properties.setRetryMaxDelaySeconds(120);
        MissionMemoryEmbeddingBackoffPolicy policy = new MissionMemoryEmbeddingBackoffPolicy(properties);
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);

        assertThat(policy.nextAttemptAt(now, 1)).isEqualTo(now.plusSeconds(15));
        assertThat(policy.nextAttemptAt(now, 4)).isEqualTo(now.plusSeconds(120));
        assertThat(policy.nextAttemptAt(now, 10)).isEqualTo(now.plusSeconds(120));
    }
}
