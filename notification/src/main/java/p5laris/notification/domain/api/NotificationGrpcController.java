package p5laris.notification.domain.api;

import com.p5laris.proto.notification.v1.GetNotificationsRequest;
import com.p5laris.proto.notification.v1.GetNotificationsResponse;
import com.p5laris.proto.notification.v1.MarkNotificationReadRequest;
import com.p5laris.proto.notification.v1.MarkNotificationReadResponse;
import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.RegisterFcmTokenRequest;
import com.p5laris.proto.notification.v1.RegisterFcmTokenResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.notification.core.exception.ErrorCode;
import p5laris.notification.domain.application.NotificationService;
import p5laris.notification.domain.exception.NotificationErrorCode;
import p5laris.notification.domain.exception.NotificationException;

@GrpcService
@RequiredArgsConstructor
public class NotificationGrpcController extends NotificationServiceGrpc.NotificationServiceImplBase {

    private final NotificationService notificationService;

    // 로그인한 사용자의 앱 내부 알림 목록을 조회한다.
    @Override
    public void getNotifications(
            GetNotificationsRequest request,
            StreamObserver<GetNotificationsResponse> responseObserver
    ) {
        try {
            GetNotificationsResponse response = notificationService.getNotifications(
                    request.getUserId(),
                    request.hasRead() ? request.getRead() : null,
                    request.getCursor(),
                    request.getSize()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // 알림 읽음 여부를 변경한다.
    @Override
    public void markNotificationRead(
            MarkNotificationReadRequest request,
            StreamObserver<MarkNotificationReadResponse> responseObserver
    ) {
        try {
            MarkNotificationReadResponse response = notificationService.markNotificationRead(
                    request.getUserId(),
                    request.getNotificationId(),
                    request.getRead()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    // FCM registration token을 등록하거나 갱신한다.
    @Override
    public void registerFcmToken(
            RegisterFcmTokenRequest request,
            StreamObserver<RegisterFcmTokenResponse> responseObserver
    ) {
        try {
            RegisterFcmTokenResponse response = notificationService.registerFcmToken(
                    request.getUserId(),
                    request.getToken()
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotificationException e) {
            responseObserver.onError(toStatus(e).withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private Status toStatus(NotificationException e) {
        ErrorCode errorCode = e.getErrorCode();

        if (errorCode == NotificationErrorCode.NOTIFICATION_NOT_FOUND) {
            return Status.NOT_FOUND;
        }

        if (errorCode == NotificationErrorCode.INVALID_NOTIFICATION_REQUEST
                || errorCode == NotificationErrorCode.INVALID_FCM_TOKEN) {
            return Status.INVALID_ARGUMENT;
        }

        return Status.INTERNAL;
    }
}