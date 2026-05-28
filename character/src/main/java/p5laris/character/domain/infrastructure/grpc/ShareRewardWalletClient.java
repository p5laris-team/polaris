package p5laris.character.domain.infrastructure.grpc;

import com.p5laris.proto.user.v1.EarnStarPieceRequest;
import com.p5laris.proto.user.v1.EarnStarPieceResponse;
import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import p5laris.character.domain.exception.CharacterErrorCode;
import p5laris.character.domain.exception.CharacterException;

@Slf4j
@Component
public class ShareRewardWalletClient {

    private static final String SHARE_REWARD_REASON = "SHARE_REWARD";
    private static final String SHARE_REF_TYPE = "SHARE";

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletStub;

    public WalletRewardResult earnShareReward(
            Long userId,
            Long shareLogId,
            int rewardStarPiece,
            String idempotencyKey
    ) {
        try {
            EarnStarPieceResponse response = walletStub.earnStarPiece(
                    EarnStarPieceRequest.newBuilder()
                            .setUserId(userId)
                            .setAmount(rewardStarPiece)
                            .setReason(SHARE_REWARD_REASON)
                            .setRefType(SHARE_REF_TYPE)
                            .setRefId(shareLogId)
                            .setIdempotencyKey(idempotencyKey)
                            .build()
            );

            return new WalletRewardResult(response.getStarPiece(), response.getTransactionId());
        } catch (StatusRuntimeException e) {
            log.warn("공유 보상 별조각 지급 실패. userId={}, shareLogId={}, status={}",
                    userId, shareLogId, e.getStatus().getCode(), e);
            throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
        } catch (Exception e) {
            log.warn("공유 보상 별조각 지급 실패. userId={}, shareLogId={}", userId, shareLogId, e);
            throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
        }
    }

    public int getWalletStarPiece(Long userId) {
        try {
            WalletResponse response = walletStub.getMyWallet(
                    GetMyWalletRequest.newBuilder()
                            .setUserId(userId)
                            .build()
            );
            return response.getStarPiece();
        } catch (StatusRuntimeException e) {
            log.warn("wallet 잔액 조회 실패. userId={}, status={}", userId, e.getStatus().getCode(), e);
            throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
        } catch (Exception e) {
            log.warn("wallet 잔액 조회 실패. userId={}", userId, e);
            throw new CharacterException(CharacterErrorCode.SHARE_REWARD_FAILED);
        }
    }

    public record WalletRewardResult(int starPiece, Long transactionId) {}
}
