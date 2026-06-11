package p5laris.item.domain.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import p5laris.common.entity.BaseEntity;

/**
 * ?꾩씠???ъ슜 ?대젰 ?뷀떚??
 *
 * character 紐⑤뱢?먯꽌 ?뚮큵 ?≪뀡(FEED / SLEEP / PLAY) ???뚮え??CONSUMABLE) ?꾩씠?쒖쓣 ?ъ슜???뚮쭏??
 * item 紐⑤뱢??UseItem gRPC API瑜??듯빐 ???뚯씠釉붿뿉 ?대젰??湲곕줉?쒕떎.
 *
 * idempotencyKey: ?숈씪 ?붿껌???ㅽ듃?뚰겕 ?ъ떆???깆쑝濡?以묐났 ?꾨떖?대룄 ??踰덈쭔 泥섎━?섎룄濡?蹂댁옣?쒕떎.
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

    /** ?꾩씠?쒖쓣 ?ъ슜???좎? ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ?ъ슜??UserItem ??*/
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_id", nullable = false)
    private UserItem userItem;

    /** ?ъ슜???꾩씠??ID (?몄쓽 而щ읆 ??JOIN ?놁씠 議고쉶 媛?? */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** ?ъ슜 ?섎웾 (MVP = 1) */
    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    /**
     * ?대뼡 而⑦뀓?ㅽ듃?먯꽌 ?ъ슜?먮뒗吏 (?? "CARE_ACTION").
     * ?ν썑 ?ㅼ뼇???꾨찓?몄뿉???꾩씠?쒖쓣 ?ъ슜?????뺤옣 ?ъ씤?몃줈 ?쒖슜?쒕떎.
     */
    @Column(name = "ref_type", length = 50)
    private String refType;

    /** ?대떦 而⑦뀓?ㅽ듃 PK (?? care_log_id) */
    @Column(name = "ref_id")
    private Long refId;

    /**
     * 硫깅벑?? gRPC ?대씪?댁뼵??character 紐⑤뱢)媛 ?앹꽦???꾨떖?쒕떎.
     * UNIQUE ?쒖빟?쇰줈 DB ?덈꺼?먯꽌 以묐났 ?쎌엯??諛⑹??쒕떎.
     */
    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;
}
