package p5laris.item.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.item.domain.domain.entity.UserItemPurchase;

import java.util.Optional;

public interface UserItemPurchaseRepository extends JpaRepository<UserItemPurchase, Long> {

    /** 멱등키로 기존 구매 이력을 조회한다. */
    Optional<UserItemPurchase> findByIdempotencyKey(String idempotencyKey);
}
