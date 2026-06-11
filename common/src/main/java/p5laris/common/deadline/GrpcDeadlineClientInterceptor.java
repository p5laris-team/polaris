package p5laris.common.deadline;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 클라이언트 호출 시 별도의 deadline(제한시간)이 설정되어 있지 않은 경우,
 * 설정된 기본 제한시간을 강제 주입하여 네트워크 지연 시 스레드 무한 대기 및 행(Hang) 현상을 방지하는 클라이언트 인터셉터입니다.
 * 
 * 특히 EventLogService나 AiService처럼 무거운 연산이 발생할 가능성이 있는 일부 gRPC 서비스의 호출에
 * 타이트한 기본 제한시간(기본 1.0초)을 적용하여 연계 장애를 차단하도록 `:common` 모듈로 통합 및 자동 적용하고 있습니다.
 */
public class GrpcDeadlineClientInterceptor implements ClientInterceptor {

    private final long defaultDeadlineMs;

    private static final long EVENT_LOG_DEADLINE_MS = 1000L;

    public GrpcDeadlineClientInterceptor(long defaultDeadlineMs) {
        this.defaultDeadlineMs = defaultDeadlineMs;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next
    ) {
        if (callOptions.getDeadline() == null) {
            long deadlineMs = defaultDeadlineMs;
            if (method.getFullMethodName() != null) {
                if (method.getFullMethodName().contains("EventLogService") ||
                    method.getFullMethodName().contains("AiService")) {
                    deadlineMs = EVENT_LOG_DEADLINE_MS;
                }
            }
            if (deadlineMs > 0) {
                callOptions = callOptions.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
            }
        }
        return next.newCall(method, callOptions);
    }
}
