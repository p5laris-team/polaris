package p5laris.common.tracing;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * gRPC 클라이언트 측에서 호출 시, 현재 Thread의 MDC 또는 gRPC Context 내의 traceId를
 * 다음 gRPC 호출의 메타데이터(Metadata) 헤더에 주입하여 MSA(마이크로서비스 아키텍처)의 분산 트랜잭션 추적을 유지해주는 인터셉터입니다.
 * 
 * 여러 서비스에 흩어져 중복되어 있던 인터셉터 코드를 `:common` 모듈로 통합하였습니다.
 */
public class GrpcTraceClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)
        ) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(TraceContext.TRACE_ID_METADATA_KEY, TraceContext.currentOrNew());
                super.start(responseListener, headers);
            }
        };
    }
}
