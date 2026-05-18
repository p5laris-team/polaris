package p5laris.user.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.StarPieceTransaction;

public interface StarPieceTransactionRepository extends JpaRepository<StarPieceTransaction, Long> {
}
