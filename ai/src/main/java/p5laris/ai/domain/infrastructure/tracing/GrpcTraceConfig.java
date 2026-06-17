package p5laris.ai.domain.infrastructure.tracing;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;
import p5laris.common.tracing.GrpcTraceClientInterceptor;
import p5laris.common.tracing.GrpcTraceServerInterceptor;

/**
 * AI 생성 요청의 traceId를 로그에 남기고 event-log 같은 하위 gRPC 호출에도 전파한다.
 */
@Configuration
public class GrpcTraceConfig {

    @GrpcGlobalServerInterceptor
    public ServerInterceptor traceServerInterceptor() {
        return new GrpcTraceServerInterceptor();
    }

    @GrpcGlobalClientInterceptor
    public ClientInterceptor traceClientInterceptor() {
        return new GrpcTraceClientInterceptor();
    }
}
