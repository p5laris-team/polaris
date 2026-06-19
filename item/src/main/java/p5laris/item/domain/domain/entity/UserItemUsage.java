package p5laris.item.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;

/**
 * 아이템 사용 이력 엔티티다.
 *
 * character 모듈에서 돌봄 액션(FEED / SLEEP / PLAY) 수행 때문에 소비형 아이템을 사용할 때마다
 * item 모듈의 UseItem gRPC API를 통해 이 테이블에 사용 기록을 남긴다.
 *
 * idempotencyKey는 같은 요청이 네트워크 재시도 등으로 중복 도달해도 한 번만 처리되도록 보장한다.
 */
@Entity
@Table(name = "item_usage_histories")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserItemUsage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 아이템을 사용한 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 사용한 UserItem 행 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_id", nullable = false)
    private UserItem userItem;

    /** 사용한 아이템 ID. 조회 시 불필요한 JOIN을 줄이기 위해 별도 컬럼으로 보관한다. */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 사용 수량. 현재 MVP에서는 1개 사용만 허용한다. */
    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    /**
     * 어떤 업무 흐름에서 아이템이 사용됐는지 나타낸다. 예: "CARE_ACTION".
     * 이후 여러 도메인에서 아이템을 사용할 수 있도록 확장 포인트로 둔다.
     */
    @Column(name = "ref_type", length = 50)
    private String refType;

    /** 해당 업무 흐름의 PK. 예: care_log_id */
    @Column(name = "ref_id")
    private Long refId;

    /**
     * 호출한 gRPC 클라이언트, 예를 들어 character 모듈이 생성해 전달한다.
     * UNIQUE 제약으로 DB 레벨에서도 중복 사용 기록 생성을 방지한다.
     */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;
}
