package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.PingPongRequest;
import com.p5laris.proto.user.v1.PingPongResponse;
import com.p5laris.proto.user.v1.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class UserGatewayService {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public String getUser(String value) {
        PingPongResponse response = userStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );

        return response.getMessage();
    }
}
