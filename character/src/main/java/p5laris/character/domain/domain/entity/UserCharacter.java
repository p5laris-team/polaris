package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ERD 1.4 user_characters
 * 사용자가 생성한 캐릭터를 저장한다.
 *
 * 상태값 정책 (AGENTS.md §20.1):
 * - fullness  : 포만감, 높을수록 좋음 (0~100)
 * - energy    : 에너지, 높을수록 좋음 (0~100)
 * - affection : 애정도, 높을수록 좋음 (0~100)
 *
 * 주의: ERD 초안의 hunger_status 컬럼명은 AGENTS.md 정책과 충돌하므로
 * 정책 문서 우선 원칙(AGENTS.md §3)에 따라 fullness로 대체한다.
 */
@Entity
@Table(
        name = "user_characters",
        indexes = {
                @Index(name = "idx_user_characters_user_id_active", columnList = "user_id, active"),
                @Index(name = "idx_user_characters_character_type_id", columnList = "character_type_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID (다른 도메인 직접 참조 금지 → FK만 보유) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_type_id", nullable = false)
    private CharacterType characterType;

    /** 캐릭터 이름 (1~10자) */
    @Column(nullable = false, length = 10)
    private String name;

    /** 레벨 */
    @Column(nullable = false)
    private int level;

    /** 누적 경험치 */
    @Column(nullable = false)
    private int exp;

    /**
     * 포만감 (0~100). AGENTS.md §20.1 fullness.
     * 높을수록 좋은 값. FEED 돌봄 액션으로 회복.
     */
    @Column(nullable = false)
    private int fullness;

    /**
     * 에너지 (0~100). AGENTS.md §20.1 energy.
     * 높을수록 좋은 값. SLEEP 돌봄 액션으로 회복.
     */
    @Column(nullable = false)
    private int energy;

    /**
     * 애정도 (0~100). AGENTS.md §20.1 affection.
     * 높을수록 좋은 값. PLAY 돌봄 액션으로 회복.
     */
    @Column(nullable = false)
    private int affection;

    /** 현재 활성 캐릭터 여부. 사용자당 1개만 true. */
    @Column(nullable = false)
    private boolean active;

    /** 장착한 스킨 아이템 ID (nullable) */
    @Column(name = "equipped_skin_id")
    private Long equippedSkinId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private UserCharacter(Long userId, CharacterType characterType, String name,
                          int level, int exp, int fullness, int energy, int affection, boolean active) {
        this.userId = userId;
        this.characterType = characterType;
        this.name = name;
        this.level = level;
        this.exp = exp;
        this.fullness = fullness;
        this.energy = energy;
        this.affection = affection;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── 상태 변경 메서드 ───────────────────────────────────────────────────

    /**
     * FEED 돌봄: fullness 회복. 100 초과 불가.
     */
    public void applyFeed(int amount) {
        this.fullness = Math.min(100, this.fullness + amount);
        this.updatedAt = Instant.now();
    }

    /**
     * SLEEP 돌봄: energy 회복. 100 초과 불가.
     */
    public void applySleep(int amount) {
        this.energy = Math.min(100, this.energy + amount);
        this.updatedAt = Instant.now();
    }

    /**
     * PLAY 돌봄: affection 회복. 100 초과 불가.
     */
    public void applyPlay(int amount) {
        this.affection = Math.min(100, this.affection + amount);
        this.updatedAt = Instant.now();
    }

    public void gainExp(int amount) {
        this.exp += amount;
        this.updatedAt = Instant.now();
    }

    public void equipSkin(Long skinId) {
        this.equippedSkinId = skinId;
        this.updatedAt = Instant.now();
    }

    public void unequipSkin() {
        this.equippedSkinId = null;
        this.updatedAt = Instant.now();
    }
}
