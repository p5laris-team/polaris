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
import p5laris.common.entity.BaseEntity;
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
     * seed 誘몄뀡 ?쒗뵆由우쓣 ?ㅼ젣 ?좎? 誘몄뀡?쇰줈 蹂듭궗???앹꽦?쒕떎.
     *
     * MissionTemplate? 怨듯넻 ?먮낯?닿퀬, UserMission? ?뱀젙 ?좎?媛 ?뱀젙 ?좎쭨??諛쏆? ?ㅼ젣 誘몄뀡 湲곕줉?대떎.
     * characterId???꾩옱 誘몄뀡 議고쉶 議곌굔???꾨땲??"??誘몄뀡???대뼡 罹먮┃?곌? ?쒖븞?덈뒗吏" ?④린??媛믪씠??
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

    /**
     * 寃利앹쓣 ?듦낵??AI ?먯쑉 誘몄뀡 ?꾨낫瑜??ㅼ젣 ?좎? 誘몄뀡??諛섏쁺?쒕떎.
     *
     * 泥섏쓬?먮뒗 seed template fallback row濡???ν븯吏留?
     * AI ?꾨낫媛 ?덉쟾 寃利앹쓣 ?듦낵?섎㈃ template ?곌껐???딄퀬 AI媛 留뚮뱺 ?쒕ぉ/?ㅻ챸/臾멸뎄瑜??ъ슜?쒕떎.
     * 蹂댁긽? AI媛 ?꾨땲??mission ?쒕퉬?ㅼ쓽 ?쒖씠?꾨퀎 蹂댁긽 ?뺤콉?쇰줈 怨꾩궛??媛믩쭔 諛쏅뒗??
     */
    public void applyGeneratedMission(
            Long aiGenerationId,
            String title,
            String description,
            String characterMessage,
            String completionCharacterResponse,
            MissionCategoryType category,
            MissionDifficultyType difficulty,
            int rewardStarPiece
    ) {
        this.missionTemplateId = null;
        this.aiGenerationId = aiGenerationId;
        this.title = title;
        this.description = description;
        this.characterMessage = characterMessage;
        this.completionCharacterResponse = completionCharacterResponse;
        this.category = category;
        this.difficulty = difficulty;
        this.rewardStarPiece = rewardStarPiece;
    }

    // OFFERED/ANSWERING ?곹깭 誘몄뀡??REJECTED濡?諛붽씀怨?嫄곗젅 ?쒓컖??湲곕줉?쒕떎.
    public void reject(LocalDateTime rejectedAt) {
        this.status = UserMissionStatus.REJECTED;
        this.rejectedAt = rejectedAt;
    }

    // OFFERED ?곹깭 誘몄뀡???꾨즺 吏덈Ц ?듬? 以묒씤 ANSWERING ?곹깭濡?諛붽씔??
    public void startAnswering(LocalDateTime completionStartedAt) {
        this.status = UserMissionStatus.ANSWERING;
        this.completionStartedAt = completionStartedAt;
    }

    // ?듬????쒖텧??誘몄뀡??COMPLETED ?곹깭濡?諛붽씀怨??꾨즺 ?쒓컖??湲곕줉?쒕떎.
    public void complete(LocalDateTime completedAt) {
        this.status = UserMissionStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    // wallet 蹂댁긽 吏湲됯퉴吏 ?깃났???? 媛숈? 誘몄뀡??蹂댁긽???ㅼ떆 吏湲됰릺吏 ?딅룄濡?硫깅벑?ㅻ? ?④릿??
    public void recordRewardPaid(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    // idempotency_key媛 ?덉쑝硫?mission 愿?먯뿉?쒕뒗 蹂댁긽 吏湲됱씠 ?꾨즺??誘몄뀡?쇰줈 蹂몃떎.
    public boolean isRewardPaid() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }

    // ?꾩옱 誘몄뀡??嫄곗젅 媛?ν븳 ?쒖븞 ?곹깭?몄? ?뺤씤?쒕떎.
    public boolean isOffered() {
        return status == UserMissionStatus.OFFERED;
    }

    // ?꾨즺 吏덈Ц ?몄뀡???대? ?쒖옉???곹깭?몄? ?뺤씤?쒕떎.
    public boolean isAnswering() {
        return status == UserMissionStatus.ANSWERING;
    }

    // ?대? ?꾨즺 泥섎━??誘몄뀡?몄? ?뺤씤?쒕떎.
    public boolean isCompleted() {
        return status == UserMissionStatus.COMPLETED;
    }
}
