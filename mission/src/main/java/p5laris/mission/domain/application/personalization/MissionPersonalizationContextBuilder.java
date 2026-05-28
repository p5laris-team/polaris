package p5laris.mission.domain.application.personalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.MissionFeedback;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.MissionDifficultyType;
import p5laris.mission.domain.domain.enums.MissionFeedbackReaction;
import p5laris.mission.domain.domain.enums.MissionFeedbackType;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.MissionFeedbackRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.infrastructure.grpc.OnboardingProfileClient;
import p5laris.mission.domain.infrastructure.grpc.OnboardingProfileClient.OnboardingProfileSnapshot;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 자율 미션 생성을 위한 개인화 입력값을 한 곳에서 조립한다.
 *
 * 최근 미션, 완료 답변, 피드백은 한 번씩 묶어서 조회해 AI context를 만들고,
 * 외부 user 모듈의 온보딩 조회가 실패해도 빈 context로 미션 생성을 계속한다.
 */
@Component
@RequiredArgsConstructor
public class MissionPersonalizationContextBuilder {

    private static final int RECENT_MISSION_LIMIT = 10;
    private static final int RECENT_ANSWER_MAX_LENGTH = 120;
    private static final String EMPTY_JSON_OBJECT = "{}";

    private final OnboardingProfileClient onboardingProfileClient;
    private final UserMissionRepository userMissionRepository;
    private final MissionCompletionAnswerRepository missionCompletionAnswerRepository;
    private final MissionFeedbackRepository missionFeedbackRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MissionPersonalizationContext build(Long userId, LocalDate missionDate) {
        LocalDate targetDate = missionDate == null ? LocalDate.now(clock) : missionDate;
        OnboardingProfileSnapshot onboarding = onboardingProfileClient.findProfile(userId).orElse(null);
        List<UserMission> recentMissions = userMissionRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(0, RECENT_MISSION_LIMIT)
        );
        Map<Long, MissionCompletionAnswer> answerByMissionId = findAnswersByMissionId(recentMissions);
        Map<Long, List<MissionFeedback>> feedbacksByMissionId = findFeedbacksByMissionId(userId, recentMissions);
        boolean challengeAlreadyUsedToday = userMissionRepository.existsByUserIdAndMissionDateAndDifficulty(
                userId,
                targetDate,
                MissionDifficultyType.CHALLENGE
        );

        return new MissionPersonalizationContext(
                toJson(onboardingContext(onboarding)),
                toJson(recentMissionContext(targetDate, recentMissions, answerByMissionId, feedbacksByMissionId, challengeAlreadyUsedToday))
        );
    }

    private Map<String, Object> onboardingContext(OnboardingProfileSnapshot onboarding) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (onboarding == null) {
            context.put("available", false);
            return context;
        }

        context.put("available", true);
        context.put("onboardingVersion", onboarding.onboardingVersion());
        context.put("completed", onboarding.completed());
        context.put("routineGoals", onboarding.routineGoals());
        context.put("preferredTimeSlots", onboarding.preferredTimeSlots());
        context.put("missionPlaceContexts", onboarding.missionPlaceContexts());
        context.put("missionIntensity", onboarding.missionIntensity());
        context.put("avoidedMissionTags", onboarding.avoidedMissionTags());
        return context;
    }

    private Map<String, Object> recentMissionContext(
            LocalDate targetDate,
            List<UserMission> recentMissions,
            Map<Long, MissionCompletionAnswer> answerByMissionId,
            Map<Long, List<MissionFeedback>> feedbacksByMissionId,
            boolean challengeAlreadyUsedToday
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("environmentContext", environmentContext(targetDate));
        context.put("policyContext", policyContext(challengeAlreadyUsedToday));
        context.put("recentMissions", recentMissions.stream()
                .map(mission -> missionContext(
                        mission,
                        answerByMissionId.get(mission.getId()),
                        feedbacksByMissionId.getOrDefault(mission.getId(), List.of())
                ))
                .toList());
        return context;
    }

    private Map<String, Object> environmentContext(LocalDate targetDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("date", targetDate.toString());
        context.put("dayOfWeek", targetDate.getDayOfWeek().name());
        context.put("timeSlot", timeSlot(now.getHour()));
        context.put("weather", null);
        return context;
    }

    private Map<String, Object> policyContext(boolean challengeAlreadyUsedToday) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("rewardPolicy", Map.of(
                "EASY", 10,
                "NORMAL", 15,
                "CHALLENGE", 30
        ));
        context.put("maxChallengePerDay", 1);
        context.put("challengeAlreadyUsedToday", challengeAlreadyUsedToday);
        context.put("allowedDifficulties", challengeAlreadyUsedToday
                ? List.of("EASY", "NORMAL")
                : List.of("EASY", "NORMAL", "CHALLENGE"));
        return context;
    }

    private Map<String, Object> missionContext(
            UserMission mission,
            MissionCompletionAnswer answer,
            List<MissionFeedback> feedbacks
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("title", mission.getTitle());
        context.put("category", mission.getCategory().name());
        context.put("difficulty", mission.getDifficulty().name());
        context.put("status", mission.getStatus().name());
        context.put("missionDate", mission.getMissionDate().toString());
        context.put("liked", hasReaction(feedbacks, MissionFeedbackReaction.LIKE));
        context.put("disliked", hasReaction(feedbacks, MissionFeedbackReaction.DISLIKE));
        context.put("rejectionReason", rejectionReason(feedbacks));

        if (mission.getStatus() == UserMissionStatus.COMPLETED && answer != null && answer.isAnswered()) {
            context.put("answerPreview", truncate(answer.getAnswerText(), RECENT_ANSWER_MAX_LENGTH));
        }
        return context;
    }

    private Map<Long, MissionCompletionAnswer> findAnswersByMissionId(List<UserMission> missions) {
        if (missions.isEmpty()) {
            return Map.of();
        }

        List<Long> missionIds = missions.stream()
                .map(UserMission::getId)
                .toList();
        return missionCompletionAnswerRepository.findByMissionIdIn(missionIds).stream()
                .collect(Collectors.toMap(MissionCompletionAnswer::getMissionId, Function.identity()));
    }

    private Map<Long, List<MissionFeedback>> findFeedbacksByMissionId(Long userId, List<UserMission> missions) {
        if (missions.isEmpty()) {
            return Map.of();
        }

        List<Long> missionIds = missions.stream()
                .map(UserMission::getId)
                .toList();
        return missionFeedbackRepository.findByUserIdAndMissionIdIn(userId, missionIds).stream()
                .collect(Collectors.groupingBy(MissionFeedback::getMissionId));
    }

    private boolean hasReaction(List<MissionFeedback> feedbacks, MissionFeedbackReaction reaction) {
        return feedbacks.stream()
                .anyMatch(feedback -> feedback.getFeedbackType() == MissionFeedbackType.SATISFACTION
                        && feedback.getReaction() == reaction);
    }

    private String rejectionReason(List<MissionFeedback> feedbacks) {
        return feedbacks.stream()
                .filter(feedback -> feedback.getFeedbackType() == MissionFeedbackType.REJECTION)
                .map(MissionFeedback::getReasonCode)
                .filter(reasonCode -> reasonCode != null)
                .map(Enum::name)
                .findFirst()
                .orElse(null);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String timeSlot(int hour) {
        if (hour < 6) {
            return "DAWN";
        }
        if (hour < 12) {
            return "MORNING";
        }
        if (hour < 18) {
            return "AFTERNOON";
        }
        if (hour < 22) {
            return "EVENING";
        }
        return "NIGHT";
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return EMPTY_JSON_OBJECT;
        }
    }
}
