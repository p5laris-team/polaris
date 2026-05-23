package p5laris.gateway.domain.notification.api.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * notification REST API에서 사용하는 요청/응답 DTO 모음이다.
 */
public class NotificationDto {

    /**
     * 앱 내부 알림 목록 응답이다.
     */
    public record NotificationsResponse(
            List<NotificationItem> items,
            PageInfo pageInfo
    ) {
    }

    /**
     * 알림 목록의 단일 항목이다.
     */
    public record NotificationItem(
            Long id,
            String notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            Boolean read,
            String createdAt
    ) {
    }

    /**
     * cursor 기반 페이지 정보다.
     */
    public record PageInfo(
            Long nextCursor,
            Boolean hasNext,
            Integer size
    ) {
    }

    /**
     * 알림 읽음 여부 변경 요청이다.
     */
    public record UpdateNotificationReadRequest(
            @NotNull
            Boolean read
    ) {
    }

    /**
     * 알림 읽음 여부 변경 응답이다.
     */
    public record UpdateNotificationReadResponse(
            Long id,
            Boolean read,
            String updatedAt
    ) {
    }

    /**
     * FCM registration token 등록/갱신 요청이다.
     */
    public record RegisterFcmTokenRequest(
            @NotBlank
            String token
    ) {
    }

    /**
     * FCM registration token 등록/갱신 응답이다.
     */
    public record RegisterFcmTokenResponse(
            Long id,
            String createdAt
    ) {
    }
}