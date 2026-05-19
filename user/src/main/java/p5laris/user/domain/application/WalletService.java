package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.domain.repository.StarPieceTransactionRepository;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.repository.WalletRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final StarPieceTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Wallet getMyWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));
    }

    @Transactional
    public StarPieceTransaction earnStarPiece(Long userId, int amount, String reason, String refType, Long refId, String idempotencyKey) {
        if (amount < 0) throw new UserException(UserErrorCode.EARN_AMOUNT_MUST_BE_POSITIVE);
        
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));

        // 보상 지급
        wallet.addStarPiece(amount);

        // 별조각 거래내역에 추가
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
                
        return transactionRepository.save(tx);
    }

    @Transactional
    public Long spendStarPiece(Long userId, int amount, String reason, String refType, Long refId, String idempotencyKey) {
        if (amount < 0) throw new IllegalArgumentException("Amount must be positive");
        
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));

        if (wallet.getStarPiece() < amount) {
            throw new UserException(UserErrorCode.STAR_PIECE_NOT_ENOUGH);
        }

        // 별조각 차감
        wallet.useStarPiece(amount);

        // 별조각 거래내역에 추가
        StarPieceTransaction tx = StarPieceTransaction.builder()
                .userId(userId)
                .transactionType("SPEND")
                .amount(-amount)
                .balanceAfter(wallet.getStarPiece())
                .reason(reason)
                .refType(refType)
                .refId(refId)
                .idempotencyKey(idempotencyKey)
                .build();
                
        transactionRepository.save(tx);
        return tx.getId();
    }
}
