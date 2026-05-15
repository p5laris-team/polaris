package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.HealthStatus;
import com.p5laris.proto.user.v1.PingPongRequest;
import com.p5laris.proto.user.v1.PingPongResponse;
import com.p5laris.proto.user.v1.UserServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class UserGrpcController extends UserServiceGrpc.UserServiceImplBase {

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