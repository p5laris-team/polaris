package p5laris.item.domain.api;

import com.p5laris.proto.item.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.item.domain.application.ItemService;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ItemGrpcController extends ItemServiceGrpc.ItemServiceImplBase {

    private static final String INTERNAL_ERROR_DESCRIPTION = "아이템 서비스 처리 중 오류가 발생했습니다.";

    private final ItemService itemService;

    @Override
    public void getItems(GetItemsRequest request, StreamObserver<GetItemsResponse> responseObserver) {
        try {
            GetItemsResponse response = itemService.getItems(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("아이템 목록 조회", e));
        }
    }

    @Override
    public void getUserItems(GetUserItemsRequest request, StreamObserver<GetUserItemsResponse> responseObserver) {
        try {
            GetUserItemsResponse response = itemService.getUserItems(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(internalError("보유 아이템 목록 조회", e));
        }
    }

    @Override
    public void purchaseItem(PurchaseItemRequest request, StreamObserver<PurchaseItemResponse> responseObserver) {
        try {
            PurchaseItemResponse response = itemService.purchaseItem(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (p5laris.item.domain.exception.ItemException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            responseObserver.onError(internalError("아이템 구매", e));
        }
    }

    @Override
    public void useItem(UseItemRequest request, StreamObserver<UseItemResponse> responseObserver) {
        try {
            UseItemResponse response = itemService.useItem(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (p5laris.item.domain.exception.ItemException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("아이템 사용", e));
        }
    }

    @Override
    public void getSkinAssets(GetSkinAssetsRequest request, StreamObserver<GetSkinAssetsResponse> responseObserver) {
        try {
            GetSkinAssetsResponse response = itemService.getSkinAssets(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (p5laris.item.domain.exception.ItemException e) {
            responseObserver.onError(toStatus(e).withDescription(safeDescription(e)).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(internalError("스킨 에셋 조회", e));
        }
    }

    private Status toStatus(p5laris.item.domain.exception.ItemException e) {
        p5laris.item.core.exception.ErrorCode errorCode = e.getErrorCode();
        if (errorCode instanceof p5laris.item.domain.exception.ItemErrorCode) {
            return switch ((p5laris.item.domain.exception.ItemErrorCode) errorCode) {
                case ITEM_NOT_FOUND, USER_ITEM_NOT_FOUND -> Status.NOT_FOUND;
                case ITEM_ALREADY_OWNED -> Status.ALREADY_EXISTS;
                case STAR_PIECE_NOT_ENOUGH, ITEM_QUANTITY_NOT_ENOUGH -> Status.FAILED_PRECONDITION;
                case WALLET_SERVICE_CALL_FAILED -> Status.UNAVAILABLE;
                case INVALID_ITEM_TYPE, INVALID_CHARACTER_TYPE -> Status.INVALID_ARGUMENT;
            };
        }
        return Status.INTERNAL;
    }

    private String safeDescription(p5laris.item.domain.exception.ItemException e) {
        p5laris.item.core.exception.ErrorCode errorCode = e.getErrorCode();
        if (errorCode instanceof Enum<?>) {
            return ((Enum<?>) errorCode).name();
        }
        return errorCode.getCode();
    }

    private RuntimeException internalError(String operation, Exception e) {
        log.error("아이템 gRPC 처리 중 알 수 없는 예외가 발생했습니다. operation={}", operation, e);
        return Status.INTERNAL.withDescription(INTERNAL_ERROR_DESCRIPTION).asRuntimeException();
    }
}
