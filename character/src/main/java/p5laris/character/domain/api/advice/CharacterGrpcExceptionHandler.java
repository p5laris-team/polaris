package p5laris.character.domain.api.advice;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import p5laris.character.domain.exception.CharacterException;

@GrpcAdvice
public class CharacterGrpcExceptionHandler {

    @GrpcExceptionHandler(CharacterException.class)
    public StatusRuntimeException handleCharacterException(CharacterException e) {
        // 커스텀 에러 코드를 Description에 담아 INVALID_ARGUMENT로 전달
        return Status.INVALID_ARGUMENT
                .withDescription("[" + e.getErrorCode().getCode() + "] " + e.getMessage())
                .asRuntimeException();
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public StatusRuntimeException handleIllegalArgumentException(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT
                .withDescription(e.getMessage())
                .asRuntimeException();
    }
}
