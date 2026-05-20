package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import com.p5laris.proto.user.v1.SpendStarPieceRequest;
import com.p5laris.proto.user.v1.SpendStarPieceResponse;
import com.p5laris.proto.user.v1.EarnStarPieceRequest;
import com.p5laris.proto.user.v1.EarnStarPieceResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.WalletService;
import p5laris.user.domain.domain.entity.Wallet;
import p5laris.user.domain.domain.entity.StarPieceTransaction;
import p5laris.user.domain.exception.UserException;

@GrpcService
@RequiredArgsConstructor
public class WalletGrpcController extends WalletServiceGrpc.WalletServiceImplBase {

    private final WalletService walletService;

    @Override
    public void getMyWallet(GetMyWalletRequest request, StreamObserver<WalletResponse> responseObserver) {
        try {
            Wallet wallet = walletService.getMyWallet(request.getUserId());
            
            WalletResponse response = WalletResponse.newBuilder()
                    .setStarPiece(wallet.getStarPiece())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void spendStarPiece(SpendStarPieceRequest request, StreamObserver<SpendStarPieceResponse> responseObserver) {
        try {
            Long txId = walletService.spendStarPiece(
                    request.getUserId(),
                    request.getAmount(),
                    request.getReason(),
                    request.getRefType().isEmpty() ? null : request.getRefType(),
                    request.getRefId() == 0 ? null : request.getRefId(),
                    request.getIdempotencyKey().isEmpty() ? null : request.getIdempotencyKey()
            );
            
            Wallet wallet = walletService.getMyWallet(request.getUserId());
            
            SpendStarPieceResponse response = SpendStarPieceResponse.newBuilder()
                    .setStarPiece(wallet.getStarPiece())
                    .setTransactionId(txId)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void earnStarPiece(EarnStarPieceRequest request, StreamObserver<EarnStarPieceResponse> responseObserver) {
        try {
            StarPieceTransaction tx = walletService.earnStarPiece(
                    request.getUserId(),
                    request.getAmount(),
                    request.getReason(),
                    request.getRefType().isEmpty() ? null : request.getRefType(),
                    request.getRefId() == 0 ? null : request.getRefId(),
                    request.getIdempotencyKey().isEmpty() ? null : request.getIdempotencyKey()
            );
            
            Wallet wallet = walletService.getMyWallet(request.getUserId());
            
            EarnStarPieceResponse response = EarnStarPieceResponse.newBuilder()
                    .setStarPiece(wallet.getStarPiece())
                    .setTransactionId(tx.getId())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (UserException e) {
            String errorCodeName = e.getErrorCode() instanceof Enum ? ((Enum<?>) e.getErrorCode()).name() : e.getErrorCode().getCode();
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(errorCodeName).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
