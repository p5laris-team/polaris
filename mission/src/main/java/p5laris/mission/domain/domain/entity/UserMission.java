package p5laris.mission.domain.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import p5laris.mission.core.entity.BaseEntity;
import p5laris.mission.domain.domain.enums.MissionCategoryType;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_missions")
public class UserMission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "mission_template_id")
    private Long missionTemplateId;

    @Column(name = "ai_generation_id")
    private Long aiGenerationId;

    @Column(name = "mission_date", nullable = false)
    private LocalDate missionDate;

    @Column(name = "stack_order", nullable = false)
    private int stackOrder;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "character_message", nullable = false, columnDefinition = "text")
    private String characterMessage;

    @Column(name = "completion_character_response", columnDefinition = "text")
    private String completionCharacterResponse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MissionCategoryType category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MissionDifficultyType difficulty;

    @Column(name = "reward_star_piece", nullable = false)
    private int rewardStarPiece;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserMissionStatus status;

    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(name = "completion_started_at")
    private LocalDateTime completionStartedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    /**
     * seed 미션 템플릿을 실제 유저 미션으로 복사해 생성한다.
     *
     * MissionTemplate은 공통 원본이고, UserMission은 특정 유저가 특정 날짜에 받은 실제 미션 기록이다.
     * characterId는 현재 미션 조회 조건이 아니라 "이 미션을 어떤 캐릭터가 제안했는지" 남기는 값이다.
     */
    public static UserMission offerFromTemplate(
            Long userId,
            Long characterId,
            LocalDate missionDate,
            int stackOrder,
            MissionTemplate template,
            LocalDateTime now
    ) {
        UserMission mission = new UserMission();
        mission.userId = userId;
        mission.characterId = characterId;
        mission.missionTemplateId = template.getId();
        mission.missionDate = missionDate;
        mission.stackOrder = stackOrder;
        mission.title = template.getBaseTitle();
        mission.description = template.getBaseDescription();
        mission.characterMessage = template.getFallbackCharacterMessage();
        mission.completionCharacterResponse = template.getFallbackCompletionResponse();
        mission.category = template.getCategory();
        mission.difficulty = template.getDifficulty();
        mission.rewardStarPiece = template.getRewardStarPiece();
        mission.status = UserMissionStatus.OFFERED;
        mission.offeredAt = now;
        return mission;
    }

    // OFFERED 상태 미션을 REJECTED로 바꾸고 거절 시각을 기록한다.
    public void reject(LocalDateTime rejectedAt) {
        this.status = UserMissionStatus.REJECTED;
        this.rejectedAt = rejectedAt;
    }

    // 현재 미션이 거절 가능한 제안 상태인지 확인한다.
    public boolean isOffered() {
        return status == UserMissionStatus.OFFERED;
    }
}
