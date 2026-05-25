package p5laris.mission.domain.domain.enums;

/**
 * 미션 보상 outbox의 처리 상태다.
 *
 * PENDING은 재처리 대기, PROCESSING은 발송 중, SUCCEEDED는 지급 완료, FAILED는 최대 재시도 초과 상태를 의미한다.
 */
public enum MissionRewardOutboxStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED
}
