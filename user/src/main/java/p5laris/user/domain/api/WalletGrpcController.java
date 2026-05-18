package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.WalletService;
import p5laris.user.domain.domain.Wallet;

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
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
