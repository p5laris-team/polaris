package p5laris.item.domain.application;

import com.p5laris.proto.user.v1.CheckTransactionRequest;
import com.p5laris.proto.user.v1.CheckTransactionResponse;
import com.p5laris.proto.user.v1.EarnStarPieceRequest;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.item.domain.domain.entity.UserItemPurchase;
import p5laris.item.domain.domain.repository.UserItemPurchaseRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemPurchaseSagaScheduler {

    private final UserItemPurchaseRepository userItemPurchaseRepository;
    private final TransactionTemplate transactionTemplate;

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletStub;

    @Scheduled(fixedDelayString = "10000")
    public void recoverUnknownPurchases() {
        List<UserItemPurchase> unknownPurchases = userItemPurchaseRepository.findUnknownPurchases(LocalDateTime.now());
        
        for (UserItemPurchase purchase : unknownPurchases) {
            try {
                // 1. 유저 지갑에서 결제가 실제로 이루어졌는지 확인
                CheckTransactionResponse checkResponse = walletStub.checkTransaction(
                        CheckTransactionRequest.newBuilder()
                                .setIdempotencyKey(purchase.getIdempotencyKey())
                                .build()
                );
                
                transactionTemplate.execute(status -> {
                    UserItemPurchase p = userItemPurchaseRepository.findById(purchase.getId()).orElseThrow();
                    
                    if (checkResponse.getExists()) {
                        // 2. 결제가 이루어졌다면 환불 진행 (보상 트랜잭션)
                        log.info("Found orphan purchase transaction. Refunding {} star pieces for userId={}", 
                                p.getPrice(), p.getUserId());
                                
                        walletStub.earnStarPiece(
                                EarnStarPieceRequest.newBuilder()
                                        .setUserId(p.getUserId())
                                        .setAmount(p.getPrice())
                                        .setReason("REFUND_ITEM_PURCHASE")
                                        .setRefType("ITEM")
                                        .setRefId(p.getItemId())
                                        .setIdempotencyKey("REFUND_" + p.getIdempotencyKey())
                                        .build()
                        );
                        
                        p.updateStatus("REFUNDED");
                    } else {
                        // 3. 결제가 안 되었다면 실패 처리
                        p.updateStatus("FAILED");
                    }
                    
                    return userItemPurchaseRepository.save(p);
                });
                
            } catch (Exception e) {
                log.error("Failed to recover unknown purchase: {}", purchase.getId(), e);
                
                transactionTemplate.execute(status -> {
                    UserItemPurchase p = userItemPurchaseRepository.findById(purchase.getId()).orElseThrow();
                    int attempt = p.getAttemptCount() + 1;
                    long backoffMinutes = (long) Math.pow(2, attempt - 1);
                    p.updateStatusWithRetry("UNKNOWN", LocalDateTime.now().plusMinutes(backoffMinutes));
                    return userItemPurchaseRepository.save(p);
                });
            }
        }
    }
}
