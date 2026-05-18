package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.GetMyWalletRequest;
import com.p5laris.proto.user.v1.WalletResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import p5laris.gateway.domain.user.api.dto.WalletDto;

@Service
public class WalletGatewayService {

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletServiceStub;

    public WalletDto.Response getMyWallet(Long userId) {
        WalletResponse response = walletServiceStub.getMyWallet(
                GetMyWalletRequest.newBuilder().setUserId(userId).build()
        );

        return WalletDto.Response.builder()
                .starPiece(response.getStarPiece())
                .build();
    }
}
