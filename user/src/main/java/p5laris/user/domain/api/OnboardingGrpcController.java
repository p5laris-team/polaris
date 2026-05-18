package p5laris.user.domain.api;

import com.p5laris.proto.user.v1.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import p5laris.user.domain.application.OnboardingService;

@GrpcService
@RequiredArgsConstructor
public class OnboardingGrpcController extends OnboardingServiceGrpc.OnboardingServiceImplBase {

    private final OnboardingService onboardingService;

    @Override
    public void getQuestions(GetQuestionsRequest request, StreamObserver<GetQuestionsResponse> responseObserver) {
        try {
            responseObserver.onNext(onboardingService.getQuestions());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<GetProfileResponse> responseObserver) {
        try {
            responseObserver.onNext(onboardingService.getProfile(request.getUserId()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void saveProfile(SaveProfileRequest request, StreamObserver<SaveProfileResponse> responseObserver) {
        try {
            responseObserver.onNext(onboardingService.saveProfile(request.getUserId(), request.getProfile()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
