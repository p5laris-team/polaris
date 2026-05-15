package p5laris.gateway.domain.ai.infrastructure.grpc;

import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.PingPongRequest;
import com.p5laris.proto.ai.v1.PingPongResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class AiGatewayService {

    @GrpcClient("ai")
    private AiServiceGrpc.AiServiceBlockingStub aiStub;

    public String getAi(String value) {
        PingPongResponse response = aiStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );

        return response.getMessage();
    }
}
