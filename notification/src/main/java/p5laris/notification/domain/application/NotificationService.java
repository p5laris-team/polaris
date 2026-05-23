package p5laris.notification.domain.application;

import com.p5laris.proto.notification.v1.GetNotificationsResponse;
import com.p5laris.proto.notification.v1.MarkNotificationReadResponse;
import com.p5laris.proto.notification.v1.PageInfo;
import com.p5laris.proto.notification.v1.RegisterFcmTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.notification.domain.domain.entity.FcmDeviceToken;
import p5laris.notification.domain.domain.entity.Notification;
import p5laris.notification.domain.domain.enums.FcmPlatform;
import p5laris.notification.domain.domain.enums.NotificationTargetType;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;
import p5laris.notification.domain.domain.repository.NotificationRepository;
import p5laris.notification.domain.exception.NotificationErrorCode;
import p5laris.notification.domain.exception.NotificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final long EMPTY_CURSOR = 0L;

    private final NotificationRepository notificationRepository;
    private final FcmDeviceTokenRepository fcmDeviceTokenRepository;

    @Transactional(readOnly = true)
    public GetNotificationsResponse getNotifications(
            Long userId,
            Boolean read,
            Long cursor,
            int size
    ) {
        validateUserId(userId);

        int pageSize = normalizeSize(size);
        int querySize = pageSize + 1;
        PageRequest pageRequest = PageRequest.of(0, querySize);

        List<Notification> notifications = findNotifications(userId, read, cursor, pageRequest);

        boolean hasNext = notifications.size() > pageSize;
        List<Notification> pageItems = hasNext
                ? notifications.subList(0, pageSize)
                : notifications;

        long nextCursor = hasNext && !pageItems.isEmpty()
                ? pageItems.get(pageItems.size() - 1).getId()
                : EMPTY_CURSOR;

        return GetNotificationsResponse.newBuilder()
                .addAllNotifications(
                        pageItems.stream()
                                .map(this::toProtoNotification)
                                .toList()
                )
                .setPageInfo(
                        PageInfo.newBuilder()
                                .setNextCursor(nextCursor)
                                .setHasNext(hasNext)
                                .setSize(pageSize)
                                .build()
                )
                .build();
    }

    @Transactional
    public MarkNotificationReadResponse markNotificationRead(
            Long userId,
            Long notificationId,
            boolean read
    ) {
        validateUserId(userId);
        validateNotificationId(notificationId);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        if (read) {
            notification.markRead();
        } else {
            notification.markUnread();
        }

        return MarkNotificationReadResponse.newBuilder()
                .setId(notification.getId())
                .setRead(notification.isRead())
                .setUpdatedAt(toString(notification.getUpdatedAt()))
                .build();
    }

    @Transactional
    public RegisterFcmTokenResponse registerFcmToken(
            Long userId,
            String token
    ) {
        validateUserId(userId);
        validateToken(token);

        String tokenHash = sha256(token);

        FcmDeviceToken deviceToken = fcmDeviceTokenRepository.findByTokenHash(tokenHash)
                .map(existingToken -> {
                    existingToken.refresh(userId, token, FcmPlatform.WEB);
                    return existingToken;
                })
                .orElseGet(() -> fcmDeviceTokenRepository.save(
                        FcmDeviceToken.builder()
                                .userId(userId)
                                .fcmToken(token)
                                .tokenHash(tokenHash)
                                .platform(FcmPlatform.WEB)
                                .build()
                ));

        return RegisterFcmTokenResponse.newBuilder()
                .setId(deviceToken.getId())
                .setCreatedAt(toString(deviceToken.getCreatedAt()))
                .build();
    }

    private List<Notification> findNotifications(
            Long userId,
            Boolean read,
            Long cursor,
            PageRequest pageRequest
    ) {
        boolean hasCursor = cursor != null && cursor > 0;

        if (read == null && !hasCursor) {
            return notificationRepository.findByUserIdOrderByIdDesc(userId, pageRequest);
        }

        if (read == null) {
            return notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(
                    userId,
                    cursor,
                    pageRequest
            );
        }

        if (!hasCursor) {
            return notificationRepository.findByUserIdAndReadOrderByIdDesc(
                    userId,
                    read,
                    pageRequest
            );
        }

        return notificationRepository.findByUserIdAndReadAndIdLessThanOrderByIdDesc(
                userId,
                read,
                cursor,
                pageRequest
        );
    }

    private com.p5laris.proto.notification.v1.Notification toProtoNotification(Notification notification) {
        com.p5laris.proto.notification.v1.Notification.Builder builder =
                com.p5laris.proto.notification.v1.Notification.newBuilder()
                        .setId(notification.getId())
                        .setNotificationType(toProtoNotificationType(notification.getNotificationType()))
                        .setTitle(notification.getTitle())
                        .setMessage(notification.getMessage())
                        .setRead(notification.isRead())
                        .setCreatedAt(toString(notification.getCreatedAt()));

        if (notification.getTargetType() != null) {
            builder.setTargetType(toProtoTargetType(notification.getTargetType()));
        }

        if (notification.getTargetId() != null) {
            builder.setTargetId(notification.getTargetId());
        }

        return builder.build();
    }

    private com.p5laris.proto.notification.v1.NotificationType toProtoNotificationType(
            p5laris.notification.domain.domain.enums.NotificationType type
    ) {
        return switch (type) {
            case MISSION -> com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_MISSION;
            case CARE -> com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_CARE;
            case ACHIEVEMENT -> com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_ACHIEVEMENT;
            case ATTENDANCE -> com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_ATTENDANCE;
            case SHARE -> com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_SHARE;
            case SYSTEM -> com.p5laris.proto.notification.v1.NotificationType.NOTIFICATION_TYPE_SYSTEM;
        };
    }

    private com.p5laris.proto.notification.v1.TargetType toProtoTargetType(NotificationTargetType type) {
        return switch (type) {
            case MISSION -> com.p5laris.proto.notification.v1.TargetType.TARGET_TYPE_MISSION;
            case CHARACTER -> com.p5laris.proto.notification.v1.TargetType.TARGET_TYPE_CHARACTER;
            case ITEM -> com.p5laris.proto.notification.v1.TargetType.TARGET_TYPE_ITEM;
            case ACHIEVEMENT -> com.p5laris.proto.notification.v1.TargetType.TARGET_TYPE_ACHIEVEMENT;
            case SHARE -> com.p5laris.proto.notification.v1.TargetType.TARGET_TYPE_SHARE;
        };
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_REQUEST);
        }
    }

    private void validateNotificationId(Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new NotificationException(NotificationErrorCode.INVALID_NOTIFICATION_REQUEST);
        }
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new NotificationException(NotificationErrorCode.INVALID_FCM_TOKEN);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new NotificationException(NotificationErrorCode.INVALID_FCM_TOKEN);
        }
    }

    private String toString(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}