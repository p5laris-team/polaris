package p5laris.common.tracing;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import p5laris.common.deadline.GrpcDeadlineClientInterceptor;

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
