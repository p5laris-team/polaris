package p5laris.user.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.user.domain.domain.entity.Wallet;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);
}
