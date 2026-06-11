package p5laris.item.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;

/**
 * ?꾩씠??援щℓ ?대젰 ?뷀떚??
 *
 * idempotencyKey: ?숈씪 ?붿껌???ㅽ듃?뚰겕 ?ъ떆???깆쑝濡?以묐났 ?꾨떖?대룄 ??踰덈쭔 泥섎━?섎룄濡?蹂댁옣?쒕떎.
 */
@Entity
@Table(name = "item_purchase_histories")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserItemPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_id", nullable = false)
    private UserItem userItem;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int price;

    @Column(name = "star_piece", nullable = false)
    private int starPiece;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private java.time.LocalDateTime nextAttemptAt;

    public void updateStatus(String status) {
        this.status = status;
    }

    public void updateStatusWithRetry(String status, java.time.LocalDateTime nextAttemptAt) {
        this.status = status;
        this.nextAttemptAt = nextAttemptAt;
        this.attemptCount++;
    }

    public void updateSuccessData(int starPiece, Long transactionId) {
        this.starPiece = starPiece;
        this.transactionId = transactionId;
    }
}
