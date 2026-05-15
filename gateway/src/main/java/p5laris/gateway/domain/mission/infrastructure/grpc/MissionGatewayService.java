package p5laris.gateway.domain.mission.infrastructure.grpc;

import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import com.p5laris.proto.mission.v1.PingPongRequest;
import com.p5laris.proto.mission.v1.PingPongResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class MissionGatewayService {

    @GrpcClient("mission")
    private MissionServiceGrpc.MissionServiceBlockingStub missionStub;

    public String getMission(String value) {
        PingPongResponse response = missionStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );

        return response.getMessage();
    }
}
