package p5laris.user.domain.application;

import com.p5laris.proto.user.v1.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.application.event.UserEventLogEvent;
import p5laris.user.domain.domain.entity.OnboardingProfile;
import p5laris.user.domain.domain.repository.OnboardingProfileRepository;
import p5laris.user.domain.domain.enums.OnboardingQuestion;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OnboardingProfileRepository onboardingProfileRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GetQuestionsResponse getQuestions() {
        var questions = Arrays.stream(OnboardingQuestion.values())
                .map(q -> QuestionDto.newBuilder()
                        .setKey(q.name())
                        .setContent(q.getContent())
                        .addAllOptions(q.getOptions().stream()
                                .map(opt -> AnswerOptionDto.newBuilder()
                                        .setKey(opt.key())
                                        .setValue(opt.value())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return GetQuestionsResponse.newBuilder()
                .addAllQuestions(questions)
                .build();
    }

    @Transactional(readOnly = true)
    public GetProfileResponse getProfile(Long userId) {
        OnboardingProfile profile = onboardingProfileRepository.findByUserId(userId)
                .orElseGet(() -> OnboardingProfile.builder().userId(userId).build());

        return GetProfileResponse.newBuilder()
                .setProfile(toDto(profile))
                .build();
    }

    @Transactional
    public SaveProfileResponse saveProfile(Long userId, OnboardingProfileDto dto) {
        OnboardingProfile profile = onboardingProfileRepository.findByUserId(userId)
                .orElseGet(() -> OnboardingProfile.builder().userId(userId).build());
        boolean wasCompleted = profile.isCompleted();

        profile.updateProfile(
                dto.getLivingType(),
                dto.getWakeUpTime(),
                dto.getSleepTime(),
                dto.getPreferredMissionTime(),
                dto.getRoutineGoal(),
                dto.getMissionIntensity(),
                dto.getActivityPreference(),
                dto.getAnswersJson(),
                dto.getCompleted()
        );
        
        OnboardingProfile savedProfile = onboardingProfileRepository.save(profile);

        eventPublisher.publishEvent(UserEventLogEvent.onboardingProfileSaved(savedProfile));
        if (!wasCompleted && savedProfile.isCompleted()) {
            eventPublisher.publishEvent(UserEventLogEvent.onboardingCompleted(savedProfile));
        }

        return SaveProfileResponse.newBuilder()
                .setProfile(toDto(savedProfile))
                .build();
    }

    private OnboardingProfileDto toDto(OnboardingProfile profile) {
        var builder = OnboardingProfileDto.newBuilder();
        if (profile.getLivingType() != null) builder.setLivingType(profile.getLivingType());
        if (profile.getWakeUpTime() != null) builder.setWakeUpTime(profile.getWakeUpTime());
        if (profile.getSleepTime() != null) builder.setSleepTime(profile.getSleepTime());
        if (profile.getPreferredMissionTime() != null) builder.setPreferredMissionTime(profile.getPreferredMissionTime());
        if (profile.getRoutineGoal() != null) builder.setRoutineGoal(profile.getRoutineGoal());
        if (profile.getActivityPreference() != null) builder.setActivityPreference(profile.getActivityPreference());
        if (profile.getMissionIntensity() != null) builder.setMissionIntensity(profile.getMissionIntensity());
        if (profile.getAnswersJson() != null) builder.setAnswersJson(profile.getAnswersJson());
        builder.setCompleted(profile.isCompleted());
        return builder.build();
    }
}
