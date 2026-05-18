package p5laris.user.domain.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.Wallet;
import p5laris.user.domain.domain.WalletRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    // 별조각 잔액 조회
    @Transactional(readOnly = true)
    public Wallet getMyWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.WALLET_NOT_FOUND));
    }
}
