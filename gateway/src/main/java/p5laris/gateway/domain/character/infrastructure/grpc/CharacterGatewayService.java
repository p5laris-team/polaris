package p5laris.gateway.domain.character.infrastructure.grpc;

import com.p5laris.proto.character.v1.CharacterServiceGrpc;
import com.p5laris.proto.character.v1.PingPongRequest;
import com.p5laris.proto.character.v1.PingPongResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class CharacterGatewayService {

    @GrpcClient("character")
    private CharacterServiceGrpc.CharacterServiceBlockingStub characterStub;

    public String getCharacter(String value) {
        PingPongResponse response = characterStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );

        return response.getMessage();
    }
}
