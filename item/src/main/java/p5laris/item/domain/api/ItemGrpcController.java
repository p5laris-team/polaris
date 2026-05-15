package p5laris.item.domain.api;

import com.p5laris.proto.item.v1.HealthStatus;
import com.p5laris.proto.item.v1.ItemServiceGrpc;
import com.p5laris.proto.item.v1.PingPongRequest;
import com.p5laris.proto.item.v1.PingPongResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ItemGrpcController extends ItemServiceGrpc.ItemServiceImplBase {

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
