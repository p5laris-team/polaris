package p5laris.mission;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "mission.reward-outbox.enabled=false",
        "mission.reward-outbox.fixed-delay-ms=60000",
        "mission.reward-outbox.initial-delay-ms=60000",
        "mission.reward-outbox.batch-size=10",
        "mission.reward-outbox.max-attempts=5",
        "mission.reward-outbox.processing-timeout-seconds=60",
        "mission.reward-outbox.retry-initial-delay-seconds=60",
        "mission.reward-outbox.retry-max-delay-seconds=3600"
})
class MissionApplicationTests {

    @Test
    void contextLoads() {
    }

}
