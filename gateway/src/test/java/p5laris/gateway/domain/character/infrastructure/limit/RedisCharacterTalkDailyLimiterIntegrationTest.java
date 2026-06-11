package p5laris.gateway.domain.character.infrastructure.limit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import p5laris.gateway.domain.character.infrastructure.config.CharacterTalkDailyLimitProperties;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisCharacterTalkDailyLimiterIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void actualRedisScriptIncrementsAndRejectsAfterDailyLimit() {
        RedisCharacterTalkDailyLimiter limiter = new RedisCharacterTalkDailyLimiter(redisTemplate, properties(3));
        long userId = 9_001L;

        CharacterTalkLimitResult first = limiter.acquire(userId);
        CharacterTalkLimitResult second = limiter.acquire(userId);
        CharacterTalkLimitResult third = limiter.acquire(userId);
        CharacterTalkLimitResult exceeded = limiter.acquire(userId);

        assertThat(first.talkStatus()).isEqualTo(CharacterTalkLimitStatus.AVAILABLE);
        assertThat(first.remainingCount()).isEqualTo(2);
        assertThat(second.remainingCount()).isEqualTo(1);
        assertThat(third.remainingCount()).isZero();
        assertThat(exceeded.talkStatus()).isEqualTo(CharacterTalkLimitStatus.LIMIT_EXCEEDED);
        assertThat(exceeded.remainingCount()).isZero();
    }

    @Test
    void concurrentRequestsNeverGrantMoreThanConfiguredLimit() throws Exception {
        int dailyLimit = 5;
        RedisCharacterTalkDailyLimiter limiter =
                new RedisCharacterTalkDailyLimiter(redisTemplate, properties(dailyLimit));
        long userId = 9_002L;
        List<Callable<CharacterTalkLimitResult>> requests = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> (Callable<CharacterTalkLimitResult>) () -> limiter.acquire(userId))
                .toList();

        try (var executor = Executors.newFixedThreadPool(10)) {
            List<CharacterTalkLimitResult> results = executor.invokeAll(requests).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();

            assertThat(results)
                    .filteredOn(result -> result.talkStatus() == CharacterTalkLimitStatus.AVAILABLE)
                    .hasSize(dailyLimit);
            assertThat(results)
                    .filteredOn(result -> result.talkStatus() == CharacterTalkLimitStatus.LIMIT_EXCEEDED)
                    .hasSize(20 - dailyLimit);
        }
    }

    private CharacterTalkDailyLimitProperties properties(int dailyLimit) {
        CharacterTalkDailyLimitProperties properties = new CharacterTalkDailyLimitProperties();
        properties.setEnabled(true);
        properties.setBackend("redis");
        properties.setDailyLimit(dailyLimit);
        properties.setFailClosed(true);
        return properties;
    }
}
