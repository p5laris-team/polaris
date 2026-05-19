package p5laris.ai.domain.domain.enums;

/**
 * AI provider 또는 출력 검증에서 실패한 원인을 분류하는 enum이다.
 *
 * proto의 AiErrorType과 DB check constraint가 같은 값 집합을 기대한다.
 */
public enum AiErrorType {
    TIMEOUT,
    RATE_LIMIT,
    INVALID_OUTPUT,
    POLICY_VIOLATION,
    PROVIDER_ERROR,
    UNKNOWN
}
