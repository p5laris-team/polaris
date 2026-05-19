package p5laris.user.domain.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class AuthLogEventListener {

    // 트랜잭션이 성공적으로 커밋된 후에, 이 메서드를 별도 스레드에서 실행함
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AuthLogEvent event) {
        try {
            // 로깅 모듈의 gRPC 추가 되는 부분...
            // 아직은 없으니 로그로 대신 확인
            log.info("auth log event: {}", event);
        } catch (Exception e) {
            log.warn("Failed to send auth log event. eventType={}, userId={}",
                    event.eventType(), event.userId(), e);
        }
    }
}
