package p5laris.character.domain.grpc;

import com.p5laris.proto.user.v1.PingPongRequest;
import com.p5laris.proto.user.v1.PingPongResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import com.p5laris.proto.user.v1.UserServiceGrpc;

@Service
public class UserGrpcClient {

    @GrpcClient("user")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public String checkUser(String value) {
        PingPongResponse response = userStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );

        return response.getMessage();
    }
}