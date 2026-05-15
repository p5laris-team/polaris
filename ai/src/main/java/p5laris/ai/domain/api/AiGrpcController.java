package p5laris.ai.domain.api;

import com.p5laris.proto.ai.v1.AiServiceGrpc;
import com.p5laris.proto.ai.v1.HealthStatus;
import com.p5laris.proto.ai.v1.PingPongRequest;
import com.p5laris.proto.ai.v1.PingPongResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AiGrpcController extends AiServiceGrpc.AiServiceImplBase {

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
