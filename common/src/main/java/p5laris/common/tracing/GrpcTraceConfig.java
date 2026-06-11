package p5laris.common.tracing;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import p5laris.common.deadline.GrpcDeadlineClientInterceptor;

/**
 * gRPC 전역 인터셉터들을 스프링 부트 자동 설정(AutoConfiguration) 메커니즘을 통해 등록해주는 설정 클래스입니다.
 * 
 * `@ConditionalOnClass`를 활용하여 각 모듈의 클래스패스 유무에 맞춰 서버용/클라이언트용 추적 인터셉터와
 * 데드라인 제어 인터셉터가 동적으로 자동 주입되도록 구현하여, 모듈별 복사-붙여넣기 설정 중복을 최소화합니다.
 */
@Configuration
public class GrpcTraceConfig {

    @Configuration
    @ConditionalOnClass(name = "net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor")
    public static class GrpcServerTraceConfig {
        @GrpcGlobalServerInterceptor
        public ServerInterceptor traceServerInterceptor() {
            return new GrpcTraceServerInterceptor();
        }
    }

    @Configuration
    @ConditionalOnClass(name = "net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor")
    public static class GrpcClientTraceConfig {
        @Value("${grpc.default-deadline-ms:3000}")
        private long defaultDeadlineMs;

        @GrpcGlobalClientInterceptor
        public ClientInterceptor traceClientInterceptor() {
            return new GrpcTraceClientInterceptor();
        }

        @GrpcGlobalClientInterceptor
        public ClientInterceptor deadlineClientInterceptor() {
            return new GrpcDeadlineClientInterceptor(defaultDeadlineMs);
        }
    }
}
