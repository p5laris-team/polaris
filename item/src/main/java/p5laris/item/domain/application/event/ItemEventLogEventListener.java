package p5laris.item.domain.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class ItemEventLogEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ItemEventLogEvent event) {
        try {
            // TODO: Replace this with the logging module gRPC client call.
            log.info("item event log: {}", event);
        } catch (Exception e) {
            log.warn("Failed to send item event log. eventType={}, userId={}",
                    event.eventType(), event.userId(), e);
        }
    }
}
