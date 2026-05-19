package p5laris.item.domain.api;

import com.p5laris.proto.item.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.item.domain.application.ItemService;

@GrpcService
@RequiredArgsConstructor
public class ItemGrpcController extends ItemServiceGrpc.ItemServiceImplBase {

    private final ItemService itemService;

    @Override
    public void getItems(GetItemsRequest request, StreamObserver<GetItemsResponse> responseObserver) {
        try {
            GetItemsResponse response = itemService.getItems(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getUserItems(GetUserItemsRequest request, StreamObserver<GetUserItemsResponse> responseObserver) {
        try {
            GetUserItemsResponse response = itemService.getUserItems(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void purchaseItem(PurchaseItemRequest request, StreamObserver<PurchaseItemResponse> responseObserver) {
        try {
            PurchaseItemResponse response = itemService.purchaseItem(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
