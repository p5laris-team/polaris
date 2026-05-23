package p5laris.notification.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.notification.core.entity.BaseEntity;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseEntity {

    private static final int DEFAULT_DAILY_PUSH_LIMIT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "daily_push_limit", nullable = false)
    private int dailyPushLimit;

    @Builder
    public NotificationSetting(
            Long userId,
            boolean pushEnabled,
            int dailyPushLimit
    ) {
        this.userId = userId;
        this.pushEnabled = pushEnabled;
        this.dailyPushLimit = dailyPushLimit;
    }

    public static NotificationSetting defaultSetting(Long userId) {
        return NotificationSetting.builder()
                .userId(userId)
                .pushEnabled(true)
                .dailyPushLimit(DEFAULT_DAILY_PUSH_LIMIT)
                .build();
    }

    public void updatePushEnabled(boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    public void updateDailyPushLimit(int dailyPushLimit) {
        this.dailyPushLimit = dailyPushLimit;
    }
}