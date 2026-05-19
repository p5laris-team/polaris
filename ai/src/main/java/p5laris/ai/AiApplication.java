package p5laris.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import p5laris.ai.domain.infrastructure.config.AiProviderProperties;

/**
 * AI 도메인 서비스 애플리케이션이다.
 *
 * 외부 REST 요청을 직접 받는 모듈이 아니라, mission/gateway 등 내부 서비스가 gRPC로 호출하는 서버 역할을 한다.
 */
@EnableJpaAuditing
@EnableConfigurationProperties(AiProviderProperties.class)
@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }

}
