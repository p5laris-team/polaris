package p5laris.common.outbox;

import java.time.LocalDateTime;

public final class OutboxBackoffPolicy {

    private OutboxBackoffPolicy() {
    }

    public static LocalDateTime nextAttemptAt(LocalDateTime now, int attemptCount) {
        return nextAttemptAt(now, attemptCount, 60L, 1800L);
    }

    public static LocalDateTime nextAttemptAt(
            LocalDateTime now,
            int attemptCount,
            long initialDelaySeconds,
            long maxDelaySeconds
    ) {
        int safeAttemptCount = Math.max(1, attemptCount);
        int exponent = Math.min(safeAttemptCount - 1, 5);
        long delaySeconds = Math.min(
                maxDelaySeconds,
                initialDelaySeconds * (1L << exponent)
        );
        return now.plusSeconds(delaySeconds);
    }
}
