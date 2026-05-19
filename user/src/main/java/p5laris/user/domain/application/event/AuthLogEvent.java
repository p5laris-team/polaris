package p5laris.user.domain.application.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record AuthLogEvent(
        String eventType,
        Long userId,
        String refType,
        Long refId,
        Map<String, Object> metadata,
        OffsetDateTime occurredAt
) {
    // 신규 사용자 가입
    public static AuthLogEvent userSignedUp(Long userId, String provider, String role, String status) {
        return new AuthLogEvent(
                "USER_SIGNED_UP",
                userId,
                "USER",
                userId,
                Map.of(
                        "provider", provider,
                        "role", role,
                        "status", status
                ),
                OffsetDateTime.now()
        );
    }

    // 기존 사용자 로그인
    public static AuthLogEvent userLoggedIn(Long userId, String provider) {
        return new AuthLogEvent(
                "USER_LOGGED_IN",
                userId,
                "USER",
                userId,
                Map.of("provider", provider),
                OffsetDateTime.now()
        );
    }
}
