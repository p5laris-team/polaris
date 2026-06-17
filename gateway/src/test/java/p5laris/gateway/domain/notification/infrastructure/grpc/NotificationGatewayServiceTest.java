package p5laris.gateway.domain.notification.infrastructure.grpc;

import com.p5laris.proto.notification.v1.GetNotificationSettingRequest;
import com.p5laris.proto.notification.v1.GetNotificationSettingResponse;
import com.p5laris.proto.notification.v1.GetNotificationsRequest;
import com.p5laris.proto.notification.v1.GetNotificationsResponse;
import com.p5laris.proto.notification.v1.GetUnreadNotificationCountRequest;
import com.p5laris.proto.notification.v1.GetUnreadNotificationCountResponse;
import com.p5laris.proto.notification.v1.MarkAllNotificationsReadRequest;
import com.p5laris.proto.notification.v1.MarkAllNotificationsReadResponse;
import com.p5laris.proto.notification.v1.MarkNotificationReadRequest;
import com.p5laris.proto.notification.v1.MarkNotificationReadResponse;
import com.p5laris.proto.notification.v1.Notification;
import com.p5laris.proto.notification.v1.NotificationServiceGrpc;
import com.p5laris.proto.notification.v1.NotificationSettingSnapshot;
import com.p5laris.proto.notification.v1.NotificationType;
import com.p5laris.proto.notification.v1.PageInfo;
import com.p5laris.proto.notification.v1.RegisterFcmTokenRequest;
import com.p5laris.proto.notification.v1.RegisterFcmTokenResponse;
import com.p5laris.proto.notification.v1.TargetType;
import com.p5laris.proto.notification.v1.UpdateNotificationSettingRequest;
import com.p5laris.proto.notification.v1.UpdateNotificationSettingResponse;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.gateway.domain.notification.api.dto.NotificationDto;
import p5laris.gateway.domain.notification.exception.NotificationGatewayErrorCode;
import p5laris.gateway.global.exception.BusinessException;
import p5laris.gateway.global.exception.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationGatewayServiceTest {

    private NotificationServiceGrpc.NotificationServiceBlockingStub stub;
    private NotificationGatewayService service;

    @BeforeEach
    void setUp() {
        stub = mock(NotificationServiceGrpc.NotificationServiceBlockingStub.class);
        service = new NotificationGatewayService();
        ReflectionTestUtils.setField(service, "notificationStub", stub);
    }

    @Test
    void notificationQueriesMapRequestsAndResponses() {
        Notification notification = Notification.newBuilder()
                .setId(11L)
                .setNotificationType(NotificationType.NOTIFICATION_TYPE_MISSION)
                .setTitle("title")
                .setMessage("message")
                .setTargetType(TargetType.TARGET_TYPE_MISSION)
                .setTargetId(22L)
                .setRead(false)
                .setCreatedAt("2026-06-11T12:00:00")
                .build();
        when(stub.getNotifications(any())).thenReturn(GetNotificationsResponse.newBuilder()
                .addNotifications(notification)
                .setPageInfo(PageInfo.newBuilder().setNextCursor(0).setHasNext(false).setSize(20))
                .build());
        when(stub.getUnreadNotificationCount(any())).thenReturn(
                GetUnreadNotificationCountResponse.newBuilder().setUnreadCount(3).build()
        );

        var notifications = service.getNotifications(7L, false, null, 100);
        int unreadCount = service.getUnreadNotificationCount(7L);

        assertThat(notifications.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(11L);
            assertThat(item.notificationType()).isEqualTo("MISSION");
            assertThat(item.targetType()).isEqualTo("MISSION");
            assertThat(item.targetId()).isEqualTo(22L);
        });
        assertThat(notifications.pageInfo().nextCursor()).isNull();
        assertThat(unreadCount).isEqualTo(3);

        ArgumentCaptor<GetNotificationsRequest> requestCaptor =
                ArgumentCaptor.forClass(GetNotificationsRequest.class);
        verify(stub).getNotifications(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(requestCaptor.getValue().getCursor()).isZero();
        assertThat(requestCaptor.getValue().getSize()).isEqualTo(50);
        assertThat(requestCaptor.getValue().hasRead()).isTrue();
        verify(stub).getUnreadNotificationCount(
                GetUnreadNotificationCountRequest.newBuilder().setUserId(7L).build()
        );
    }

    @Test
    void notificationCommandsMapAllResponses() {
        when(stub.markNotificationRead(any())).thenReturn(MarkNotificationReadResponse.newBuilder()
                .setId(11L).setRead(true).setUpdatedAt("updated").build());
        when(stub.markAllNotificationsRead(any())).thenReturn(MarkAllNotificationsReadResponse.newBuilder()
                .setUpdatedCount(4).setUnreadCount(0).build());
        when(stub.registerFcmToken(any())).thenReturn(RegisterFcmTokenResponse.newBuilder()
                .setId(5L).setCreatedAt("created").build());
        NotificationSettingSnapshot setting = NotificationSettingSnapshot.newBuilder()
                .setPushEnabled(true)
                .setMissionOfferEnabled(true)
                .setCharacterStateEnabled(false)
                .setDailyReminderEnabled(true)
                .setQuietHoursEnabled(true)
                .setQuietHoursStart("22:00")
                .setQuietHoursEnd("07:00")
                .build();
        when(stub.getNotificationSetting(any())).thenReturn(
                GetNotificationSettingResponse.newBuilder().setSetting(setting).build()
        );
        when(stub.updateNotificationSetting(any())).thenReturn(
                UpdateNotificationSettingResponse.newBuilder().setSetting(setting).build()
        );

        assertThat(service.updateNotificationRead(
                7L, 11L, new NotificationDto.UpdateNotificationReadRequest(true)
        ).updatedAt()).isEqualTo("updated");
        assertThat(service.markAllNotificationsRead(7L).updatedAt()).isNull();
        assertThat(service.registerFcmToken(
                7L, new NotificationDto.RegisterFcmTokenRequest("token")
        ).id()).isEqualTo(5L);
        assertThat(service.getNotificationSetting(7L).quietHoursStart()).isEqualTo("22:00");
        assertThat(service.updateNotificationSetting(
                7L,
                new NotificationDto.UpdateNotificationSettingRequest(
                        true, true, false, true, true, "22:00", "07:00"
                )
        ).quietHoursEnd()).isEqualTo("07:00");

        verify(stub).markNotificationRead(
                MarkNotificationReadRequest.newBuilder()
                        .setUserId(7L).setNotificationId(11L).setRead(true).build()
        );
        verify(stub).markAllNotificationsRead(
                MarkAllNotificationsReadRequest.newBuilder().setUserId(7L).build()
        );
        verify(stub).registerFcmToken(
                RegisterFcmTokenRequest.newBuilder().setUserId(7L).setToken("token").build()
        );
        verify(stub).getNotificationSetting(
                GetNotificationSettingRequest.newBuilder().setUserId(7L).build()
        );
        verify(stub).updateNotificationSetting(
                UpdateNotificationSettingRequest.newBuilder()
                        .setUserId(7L)
                        .setPushEnabled(true)
                        .setMissionOfferEnabled(true)
                        .setCharacterStateEnabled(false)
                        .setDailyReminderEnabled(true)
                        .setQuietHoursEnabled(true)
                        .setQuietHoursStart("22:00")
                        .setQuietHoursEnd("07:00")
                        .build()
        );
    }

    @Test
    void invalidInputsAndGrpcStatusesBecomeGatewayErrors() {
        assertThatThrownBy(() -> service.getNotifications(0L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.getNotifications(1L, null, -1L, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateNotificationRead(1L, 0L, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateNotificationRead(1L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NotificationGatewayErrorCode.INVALID_NOTIFICATION_REQUEST);
        assertThatThrownBy(() -> service.registerFcmToken(
                1L, new NotificationDto.RegisterFcmTokenRequest(" ")
        )).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NotificationGatewayErrorCode.INVALID_FCM_TOKEN);

        when(stub.getUnreadNotificationCount(any()))
                .thenThrow(Status.NOT_FOUND.asRuntimeException());
        assertThatThrownBy(() -> service.getUnreadNotificationCount(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NotificationGatewayErrorCode.NOTIFICATION_NOT_FOUND);

        reset(stub);
        when(stub.getUnreadNotificationCount(any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertThatThrownBy(() -> service.getUnreadNotificationCount(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NotificationGatewayErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
    }
}
