package p5laris.gateway.domain.user.infrastructure.grpc;

import com.p5laris.proto.user.v1.AnswerOptionDto;
import com.p5laris.proto.user.v1.GetProfileRequest;
import com.p5laris.proto.user.v1.GetProfileResponse;
import com.p5laris.proto.user.v1.GetQuestionsRequest;
import com.p5laris.proto.user.v1.GetQuestionsResponse;
import com.p5laris.proto.user.v1.GetUserRequest;
import com.p5laris.proto.user.v1.GetUserResponse;
import com.p5laris.proto.user.v1.GetWeatherRegionRequest;
import com.p5laris.proto.user.v1.GetWeatherRegionResponse;
import com.p5laris.proto.user.v1.ListWeatherRegionsRequest;
import com.p5laris.proto.user.v1.ListWeatherRegionsResponse;
import com.p5laris.proto.user.v1.OnboardingProfileDto;
import com.p5laris.proto.user.v1.OnboardingServiceGrpc;
import com.p5laris.proto.user.v1.QuestionDto;
import com.p5laris.proto.user.v1.SaveProfileRequest;
import com.p5laris.proto.user.v1.SaveProfileResponse;
import com.p5laris.proto.user.v1.UpdateWeatherRegionRequest;
import com.p5laris.proto.user.v1.UpdateWeatherRegionResponse;
import com.p5laris.proto.user.v1.User;
import com.p5laris.proto.user.v1.UserServiceGrpc;
import com.p5laris.proto.user.v1.WeatherRegion;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.gateway.domain.user.api.dto.OnboardingDto;
import p5laris.gateway.domain.user.api.dto.WeatherRegionDto;
import p5laris.gateway.domain.user.exception.UserGatewayErrorCode;
import p5laris.gateway.global.exception.BusinessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserGatewayServicesTest {

    private UserServiceGrpc.UserServiceBlockingStub userStub;
    private OnboardingServiceGrpc.OnboardingServiceBlockingStub onboardingStub;
    private UserGatewayService userService;
    private OnboardingGatewayService onboardingService;

    @BeforeEach
    void setUp() {
        userStub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
        onboardingStub = mock(OnboardingServiceGrpc.OnboardingServiceBlockingStub.class);
        userService = new UserGatewayService();
        onboardingService = new OnboardingGatewayService();
        ReflectionTestUtils.setField(userService, "userServiceStub", userStub);
        ReflectionTestUtils.setField(onboardingService, "onboardingServiceStub", onboardingStub);
    }

    @Test
    void mapsUserAndWeatherRegionResponses() {
        User user = User.newBuilder()
                .setId(7L).setEmail("user@test.com").setNickname("nick")
                .setProvider("GOOGLE").setRole("USER").setStatus("ACTIVE").build();
        WeatherRegion seoul = WeatherRegion.newBuilder()
                .setCode("SEOUL").setDisplayName("서울").build();
        when(userStub.getUser(GetUserRequest.newBuilder().setUserId(7L).build()))
                .thenReturn(GetUserResponse.newBuilder().setUser(user).build());
        when(userStub.listWeatherRegions(ListWeatherRegionsRequest.getDefaultInstance()))
                .thenReturn(ListWeatherRegionsResponse.newBuilder().addRegions(seoul).build());
        when(userStub.getWeatherRegion(GetWeatherRegionRequest.newBuilder().setUserId(7L).build()))
                .thenReturn(GetWeatherRegionResponse.newBuilder().setSelected(false).build());
        when(userStub.updateWeatherRegion(UpdateWeatherRegionRequest.newBuilder()
                .setUserId(7L).setRegionCode("SEOUL").build()))
                .thenReturn(UpdateWeatherRegionResponse.newBuilder()
                        .setSelected(true).setRegion(seoul).build());

        assertThat(userService.getUser(7L).getEmail()).isEqualTo("user@test.com");
        assertThat(userService.listWeatherRegions().regions())
                .extracting(WeatherRegionDto.RegionItem::regionCode)
                .containsExactly("SEOUL");
        assertThat(userService.getWeatherRegion(7L).selected()).isFalse();
        assertThat(userService.updateWeatherRegion(
                7L, new WeatherRegionDto.UpdateRegionRequest("SEOUL")
        ).displayName()).isEqualTo("서울");
    }

    @Test
    void validatesWeatherRegionAndMapsGrpcErrors() {
        assertThatThrownBy(() -> userService.updateWeatherRegion(7L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserGatewayErrorCode.INVALID_WEATHER_REGION);

        when(userStub.getUser(GetUserRequest.newBuilder().setUserId(9L).build()))
                .thenThrow(Status.NOT_FOUND.asRuntimeException());
        assertThatThrownBy(() -> userService.getUser(9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserGatewayErrorCode.USER_NOT_FOUND);
    }

    @Test
    void mapsOnboardingQuestionsProfilesAndSaveRequests() {
        QuestionDto question = QuestionDto.newBuilder()
                .setKey("ROUTINE_GOAL")
                .setContent("goal")
                .setMultipleSelection(true)
                .setMaxSelectionCount(3)
                .addOptions(AnswerOptionDto.newBuilder().setKey("EXERCISE").setValue("exercise"))
                .build();
        OnboardingProfileDto profile = OnboardingProfileDto.newBuilder()
                .setLivingType("ALONE")
                .setMissionIntensity("LIGHT")
                .setCompleted(true)
                .setOnboardingVersion(2)
                .addRoutineGoals("EXERCISE")
                .addPreferredTimeSlots("MORNING")
                .addMissionPlaceContexts("HOME")
                .addAvoidedMissionTags("SOCIAL")
                .build();
        when(onboardingStub.getQuestions(GetQuestionsRequest.getDefaultInstance()))
                .thenReturn(GetQuestionsResponse.newBuilder().addQuestions(question).build());
        when(onboardingStub.getProfile(GetProfileRequest.newBuilder().setUserId(7L).build()))
                .thenReturn(GetProfileResponse.newBuilder().setProfile(profile).build());

        OnboardingDto.SaveProfileRequest request = new OnboardingDto.SaveProfileRequest(
                "ALONE", null, null, null, null, null, "LIGHT", null,
                true, 2, List.of("EXERCISE"), List.of("MORNING"),
                List.of("HOME"), List.of("SOCIAL")
        );
        SaveProfileRequest grpcRequest = SaveProfileRequest.newBuilder()
                .setUserId(7L)
                .setProfile(profile)
                .build();
        when(onboardingStub.saveProfile(grpcRequest))
                .thenReturn(SaveProfileResponse.newBuilder().setProfile(profile).build());

        assertThat(onboardingService.getQuestions()).singleElement().satisfies(result -> {
            assertThat(result.getKey()).isEqualTo("ROUTINE_GOAL");
            assertThat(result.getOptions()).singleElement()
                    .extracting(OnboardingDto.AnswerOption::getKey)
                    .isEqualTo("EXERCISE");
        });
        assertThat(onboardingService.getProfile(7L).getLivingType()).isEqualTo("ALONE");
        assertThat(onboardingService.saveProfile(7L, request).getRoutineGoals())
                .containsExactly("EXERCISE");
        verify(onboardingStub).saveProfile(grpcRequest);
    }
}
