package p5laris.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "spring.ai.model.embedding.text=none",
        "ai.embedding.enabled=false",
        "ai.embedding.model=gemini-embedding-001",
        "ai.embedding.dimension=768"
})
class AiApplicationTests {

    @Test
    void contextLoads() {
    }

}
