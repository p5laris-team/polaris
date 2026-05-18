package p5laris.character.domain.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD 1.5 share_cards
 * 캐릭터 카드 공유를 위한 공유 카드 정보를 저장한다.
 */
@Entity
@Table(name = "share_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 캐릭터 ID */
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    /** 공유 카드 이미지 URL (nullable) */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** 공유 URL */
    @Column(name = "share_url", nullable = false)
    private String shareUrl;

    @Builder
    private ShareCard(Long userId, Long characterId, String imageUrl, String shareUrl) {
        this.userId = userId;
        this.characterId = characterId;
        this.imageUrl = imageUrl;
        this.shareUrl = shareUrl;
    }
}
