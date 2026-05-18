package p5laris.character.domain.api;

import com.p5laris.proto.character.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.character.domain.application.CharacterService;

@GrpcService
@RequiredArgsConstructor
public class CharacterGrpcController extends CharacterServiceGrpc.CharacterServiceImplBase {

    private final CharacterService characterService;

    @Override
    public void pingPong(PingPongRequest request, StreamObserver<PingPongResponse> responseObserver) {
        PingPongResponse response = PingPongResponse.newBuilder()
                .setHealthStatus(HealthStatus.HEALTHY)
                .setMessage(request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * Get character types (API spec 4.1).
     * Returns active character types sorted by sort_order ascending.
     */
    @Override
    public void getCharacterTypes(GetCharacterTypesRequest request,
                                  StreamObserver<GetCharacterTypesResponse> responseObserver) {
        var items = characterService.getCharacterTypes()
                .stream()
                .map(ct -> CharacterTypeItem.newBuilder()
                        .setId(ct.id())
                        .setCode(ct.code())
                        .setName(ct.name())
                        .setSummary(ct.summary())
                        .setSampleLine(ct.sampleLine())
                        .setSortOrder(ct.sortOrder())
                        .build())
                .toList();

        GetCharacterTypesResponse response = GetCharacterTypesResponse.newBuilder()
                .addAllItems(items)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

