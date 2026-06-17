package p5laris.common.tracing;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC 서버 측에서 수신한 메타데이터(Metadata)의 traceId를 현재 gRPC Context 및
 * 로깅 라이브러리의 MDC(Mapped Diagnostic Context)에 매핑 및 복원하는 서버 인터셉터입니다.
 * 
 * 여러 서비스에 복사되어 있던 중복 추적 인터셉터 코드를 `:common` 모듈로 하나로 통합하였습니다.
 */
public class GrpcTraceServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String traceId = TraceContext.normalizeOrNew(headers.get(TraceContext.TRACE_ID_METADATA_KEY));
        Context context = Context.current().withValue(TraceContext.GRPC_CONTEXT_KEY, traceId);

        try (TraceContext.MdcScope ignored = TraceContext.openMdcScope(traceId)) {
            ServerCall.Listener<ReqT> listener = Contexts.interceptCall(context, call, headers, next);
            return new TraceServerCallListener<>(listener, traceId);
        }
    }

    private static final class TraceServerCallListener<ReqT>
            extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final String traceId;

        private TraceServerCallListener(ServerCall.Listener<ReqT> delegate, String traceId) {
            super(delegate);
            this.traceId = traceId;
        }

        @Override
        public void onMessage(ReqT message) {
            runWithTraceId(() -> super.onMessage(message));
        }

        @Override
        public void onHalfClose() {
            runWithTraceId(super::onHalfClose);
        }

        @Override
        public void onCancel() {
            runWithTraceId(super::onCancel);
        }

        @Override
        public void onComplete() {
            runWithTraceId(super::onComplete);
        }

        @Override
        public void onReady() {
            runWithTraceId(super::onReady);
        }

        private void runWithTraceId(Runnable action) {
            try (TraceContext.MdcScope ignored = TraceContext.openMdcScope(traceId)) {
                action.run();
            }
        }
    }
}
