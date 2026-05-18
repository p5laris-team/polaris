package p5laris.user.domain.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import p5laris.user.core.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "onboarding_profiles")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OnboardingProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    private String livingType;
    private String wakeUpTime;
    private String sleepTime;
    private String preferredMissionTime;
    private String routineGoal;
    private String activityPreference;
    private String missionIntensity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String answersJson;

    @Builder.Default
    private boolean completed = false;

    private LocalDateTime completedAt;

    public void updateProfile(String livingType, String wakeUpTime, String sleepTime, 
                              String preferredMissionTime, String routineGoal, 
                              String missionIntensity, String activityPreference,
                              String answersJson, boolean completed) {
        this.livingType = livingType;
        this.wakeUpTime = wakeUpTime;
        this.sleepTime = sleepTime;
        this.preferredMissionTime = preferredMissionTime;
        this.routineGoal = routineGoal;
        this.missionIntensity = missionIntensity;
        this.activityPreference = activityPreference;
        this.answersJson = answersJson;
        this.completed = completed;
        if (completed && this.completedAt == null) {
            this.completedAt = LocalDateTime.now();
        }
    }
}
