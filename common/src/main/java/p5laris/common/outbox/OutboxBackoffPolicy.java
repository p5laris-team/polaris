package p5laris.common.outbox;

import java.time.LocalDateTime;

/**
 * 아웃박스(Outbox) 재시도 시 지수 백오프(Exponential Backoff) 대기 시간을 계산하는 공통 정책 클래스입니다.
 * 
 * 여러 모듈(item, user, mission, character)에 흩어져 어긋나 있던 재시도 간격 계산식을 하나로 통합하여
 * 시스템 전체의 아웃박스 재시도 시간 계산 정합성을 맞춥니다.
 */
public final class OutboxBackoffPolicy {

    private OutboxBackoffPolicy() {
    }

    /**
     * 기본 딜레이값(최초 60초, 최대 1800초)을 사용하여 다음 재시도 대기 시각을 계산합니다.
     */
    public static LocalDateTime nextAttemptAt(LocalDateTime now, int attemptCount) {
        return nextAttemptAt(now, attemptCount, 60L, 1800L);
    }

    /**
     * 최초 대기 초 단위와 최대 대기 초 단위를 입력받아 지수 백오프 공식(initialDelay * 2^exponent)에 맞춰
     * 다음 재시도 예정 시각을 계산합니다. 최대 대기 시각(maxDelaySeconds)을 넘지 않도록 제한합니다.
     */
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
