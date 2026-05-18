package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.WalletRepository;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final StarPieceTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Wallet getMyWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + userId));
    }

    @Transactional
    public void earnStarPiece(Long userId, int amount, String reason, String refType, Long refId, String idempotencyKey) {
        if (amount < 0) throw new IllegalArgumentException("Earn amount must be positive");
        
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + userId));
                
        wallet.addStarPiece(amount);
        
        StarPieceTransaction tx = StarPieceTransaction.builder()
                .userId(userId)
                .transactionType("EARN")
                .amount(amount)
                .balanceAfter(wallet.getStarPiece())
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .idempotencyKey(idempotencyKey)
                .build();
                
        transactionRepository.save(tx);
    }
}
