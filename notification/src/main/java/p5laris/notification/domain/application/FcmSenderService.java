package p5laris.notification.domain.application;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.notification.domain.domain.entity.FcmDeviceToken;
import p5laris.notification.domain.domain.enums.FcmTokenDeactivatedReason;
import p5laris.notification.domain.domain.repository.FcmDeviceTokenRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmSenderService {

    private final FcmDeviceTokenRepository fcmDeviceTokenRepository;

    @Async
    @Transactional
    public void sendPushNotification(Long userId, String title, String body) {
        List<FcmDeviceToken> activeTokens = fcmDeviceTokenRepository.findByUserIdAndActiveTrue(userId);

        if (activeTokens.isEmpty()) {
            log.info("No active FCM tokens found for user: {}", userId);
            return;
        }

        for (FcmDeviceToken token : activeTokens) {
            Message message = Message.builder()
                    .setToken(token.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            try {
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Successfully sent message: {} to user: {} token: {}", response, userId, token.getId());
            } catch (FirebaseMessagingException e) {
                log.warn("Failed to send message to user: {} token: {}", userId, token.getId(), e);
                
                String errorCode = e.getErrorCode().name();
                if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                    log.info("Deactivating FCM token: {} due to errorCode: {}", token.getId(), errorCode);
                    token.deactivate(FcmTokenDeactivatedReason.UNKNOWN); // Using UNKNOWN or UNREGISTERED if available in enum
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM message to user: {}", userId, e);
            }
        }
    }
}
