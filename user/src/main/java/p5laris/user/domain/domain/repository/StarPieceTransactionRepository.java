package p5laris.user.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.StarPieceTransaction;

import java.util.Optional;

public interface StarPieceTransactionRepository extends JpaRepository<StarPieceTransaction, Long> {

    Optional<StarPieceTransaction> findByIdempotencyKey(String idempotencyKey);
}
