package p5laris.mission.domain.application;

import org.junit.jupiter.api.Test;
import p5laris.mission.domain.infrastructure.config.MissionRewardOutboxProperties;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MissionRewardBackoffPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 11, 12, 0);

    @Test
    void doublesDelayAndCapsAtConfiguredMaximum() {
        MissionRewardOutboxProperties properties = new MissionRewardOutboxProperties();
        properties.setRetryInitialDelaySeconds(60);
        properties.setRetryMaxDelaySeconds(300);
        MissionRewardBackoffPolicy policy = new MissionRewardBackoffPolicy(properties);

        assertThat(policy.nextAttemptAt(NOW, 1)).isEqualTo(NOW.plusSeconds(60));
        assertThat(policy.nextAttemptAt(NOW, 2)).isEqualTo(NOW.plusSeconds(120));
        assertThat(policy.nextAttemptAt(NOW, 3)).isEqualTo(NOW.plusSeconds(240));
        assertThat(policy.nextAttemptAt(NOW, 4)).isEqualTo(NOW.plusSeconds(300));
        assertThat(policy.nextAttemptAt(NOW, 20)).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void nonPositiveAttemptCountUsesFirstRetryDelay() {
        MissionRewardOutboxProperties properties = new MissionRewardOutboxProperties();
        properties.setRetryInitialDelaySeconds(30);
        properties.setRetryMaxDelaySeconds(300);

        assertThat(new MissionRewardBackoffPolicy(properties).nextAttemptAt(NOW, 0))
                .isEqualTo(NOW.plusSeconds(30));
    }
}
