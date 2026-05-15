package p5laris.gateway.domain.item.infrastructure.grpc;

import com.p5laris.proto.item.v1.ItemServiceGrpc;
import com.p5laris.proto.item.v1.PingPongRequest;
import com.p5laris.proto.item.v1.PingPongResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class ItemGatewayService {

    @GrpcClient("item")
    private ItemServiceGrpc.ItemServiceBlockingStub itemStub;

    public String getItem(String value) {
        PingPongResponse response = itemStub.pingPong(
                PingPongRequest.newBuilder()
                        .setMessage(value)
                        .build()
        );

        return response.getMessage();
    }
}
