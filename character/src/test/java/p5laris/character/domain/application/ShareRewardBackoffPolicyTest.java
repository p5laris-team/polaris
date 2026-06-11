package p5laris.character.domain.application;

import org.junit.jupiter.api.Test;
import p5laris.character.domain.infrastructure.config.ShareRewardOutboxProperties;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShareRewardBackoffPolicyTest {

    @Test
    void rewardRetryDelayDoublesAndStopsAtMaximum() {
        ShareRewardOutboxProperties properties = new ShareRewardOutboxProperties();
        properties.setRetryInitialDelaySeconds(60);
        properties.setRetryMaxDelaySeconds(600);
        ShareRewardBackoffPolicy policy = new ShareRewardBackoffPolicy(properties);
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 12, 0);

        assertThat(policy.nextAttemptAt(now, -1)).isEqualTo(now.plusSeconds(60));
        assertThat(policy.nextAttemptAt(now, 2)).isEqualTo(now.plusSeconds(120));
        assertThat(policy.nextAttemptAt(now, 5)).isEqualTo(now.plusSeconds(600));
        assertThat(policy.nextAttemptAt(now, 20)).isEqualTo(now.plusSeconds(600));
    }
}
