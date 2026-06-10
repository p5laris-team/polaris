package p5laris.item.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.item.domain.domain.entity.UserItemPurchase;

import java.util.Optional;

public interface UserItemPurchaseRepository extends JpaRepository<UserItemPurchase, Long> {

    /** 멱등키로 기존 구매 이력을 조회한다. */
    Optional<UserItemPurchase> findByIdempotencyKey(String idempotencyKey);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM UserItemPurchase p WHERE " +
            "(p.status = 'UNKNOWN' AND p.nextAttemptAt <= :now) OR " +
            "(p.status = 'PENDING' AND p.createdAt <= :pendingThreshold) " +
            "ORDER BY p.createdAt ASC")
    java.util.List<UserItemPurchase> findUnknownPurchases(
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
            @org.springframework.data.repository.query.Param("pendingThreshold") java.time.LocalDateTime pendingThreshold
    );
}
