package p5laris.character.domain.api;

import com.p5laris.proto.character.v1.CharacterServiceGrpc;
import com.p5laris.proto.character.v1.HealthStatus;
import com.p5laris.proto.character.v1.PingPongRequest;
import com.p5laris.proto.character.v1.PingPongResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CharacterGrpcController extends CharacterServiceGrpc.CharacterServiceImplBase {

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
