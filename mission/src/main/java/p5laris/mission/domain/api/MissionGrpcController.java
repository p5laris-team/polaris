package p5laris.mission.domain.api;

import com.p5laris.proto.mission.v1.HealthStatus;
import com.p5laris.proto.mission.v1.MissionServiceGrpc;
import com.p5laris.proto.mission.v1.PingPongRequest;
import com.p5laris.proto.mission.v1.PingPongResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class MissionGrpcController extends MissionServiceGrpc.MissionServiceImplBase {

    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
