package p5laris.mission.domain.application;

import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.CompletionAnswer;
import com.p5laris.proto.mission.v1.CompletionInputType;
import com.p5laris.proto.mission.v1.CompletionQuestion;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.GetTodayMissionsResponse;
import com.p5laris.proto.mission.v1.MissionCategory;
import com.p5laris.proto.mission.v1.MissionDifficulty;
import com.p5laris.proto.mission.v1.MissionReward;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import com.p5laris.proto.mission.v1.StartCompletionSessionResponse;
import com.p5laris.proto.mission.v1.SubmitCompletionAnswerResponse;
import com.p5laris.proto.mission.v1.TodayMission;
import com.p5laris.proto.mission.v1.WalletSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import p5laris.mission.domain.application.event.MissionEventLogEvent;
import p5laris.mission.domain.domain.entity.MissionCompletionAnswer;
import p5laris.mission.domain.domain.entity.MissionRewardOutbox;
import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionCompletionAnswerRepository;
import p5laris.mission.domain.domain.repository.MissionRewardOutboxRepository;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextClient;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextRequest;
import p5laris.mission.domain.infrastructure.grpc.AiMissionTextResult;
import p5laris.mission.domain.infrastructure.grpc.CharacterProfileClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardClient;
import p5laris.mission.domain.infrastructure.grpc.WalletRewardResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MissionService {

    /*
     * 아래 값들은 운영 토글이 아니라 현재 MVP 정책을 표현하는 도메인 상수다.
     * 배포 환경별로 달라져야 하는 값은 application.yaml/env로 분리하고,
     * 하루 제안 수/답변 길이/멱등키 prefix처럼 API 정책과 함께 움직이는 값만 코드에 둔다.
     */
    private static final int DAILY_MISSION_OFFER_LIMIT = 15;
    private static final int COMPLETION_ANSWER_MIN_LENGTH = 1;
    private static final int COMPLETION_ANSWER_MAX_LENGTH = 300;
    private static final String MISSION_REWARD_IDEMPOTENCY_KEY_PREFIX = "MISSION_REWARD:";
    private static final String MISSION_TEXT_REQUEST_ID_PREFIX = "MISSION_TEXT:";
    private static final String DEFAULT_COMPLETION_QUESTION = "해보고 나서 어땠어?";
    private static final String REJECTION_CHARACTER_MESSAGE = "괜찮아요. 다른 별을 찾아볼게요.";
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Set<UserMissionStatus> ACTIVE_STATUSES = EnumSet.of(
            UserMissionStatus.OFFERED,
            UserMissionStatus.ANSWERING
    );

    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionTemplateSelector missionTemplateSelector;
    private final UserMissionRepository userMissionRepository;
    private final MissionCompletionAnswerRepository missionCompletionAnswerRepository;
    private final MissionRewardOutboxRepository missionRewardOutboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AiMissionTextClient aiMissionTextClient;
    private final CharacterProfileClient characterProfileClient;
    private final WalletRewardClient walletRewardClient;
    private final MissionRewardDispatcher missionRewardDispatcher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * 오늘 유저에게 진행 중인 현재 미션을 조회한다.
     *
     * 현재 미션의 기준은 characterId가 아니라 userId다.
     * characterId는 "어떤 캐릭터가 제안했는지"를 기록하기 위한 값이고,
     * MVP 정책상 한 유저는 하루에 OFFERED/ANSWERING 상태 미션을 하나만 가진다.
     */
    @Transactional(readOnly = true)
    public GetCurrentMissionResponse getCurrentMission(Long userId) {
        LocalDate today = LocalDate.now(clock);

        return userMissionRepository
                .findFirstByUserIdAndMissionDateAndStatusInOrderByStackOrderDesc(
                        userId,
                        today,
                        ACTIVE_STATUSES
                )
                .map(this::toProtoMission)
                .map(mission -> GetCurrentMissionResponse.newBuilder()
                        .setMission(mission)
                        .build())
                .orElseGet(() -> GetCurrentMissionResponse.newBuilder().build());
    }

    /**
     * 오늘 유저에게 제안된 미션 stack 전체를 조회한다.
     *
     * 이 메서드는 홈/미션 히스토리 화면용 읽기 API라 상태를 바꾸지 않는다.
     * 하루 최대 15개 정책이 있으므로 pagination 없이 stackOrder 오름차순으로 반환한다.
     */
    @Transactional(readOnly = true)
    public GetTodayMissionsResponse getTodayMissions(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<UserMission> missions = userMissionRepository.findByUserIdAndMissionDateOrderByStackOrderAsc(userId, today);

        GetTodayMissionsResponse.Builder builder = GetTodayMissionsResponse.newBuilder()
                .setMissionDate(today.toString())
                .setMaxDailyOffers(DAILY_MISSION_OFFER_LIMIT)
                .setOfferedCount(missions.size())
                .setCompletedCount(countByStatus(missions, UserMissionStatus.COMPLETED))
                .setRejectedCount(countByStatus(missions, UserMissionStatus.REJECTED))
                .setRemainingOfferCount(Math.max(0, DAILY_MISSION_OFFER_LIMIT - missions.size()));

        findCurrentMissionId(missions).ifPresent(builder::setCurrentMissionId);
        missions.stream()
                .map(this::toProtoTodayMission)
                .forEach(builder::addMissions);

        return builder.build();
    }

    /**
     * 다음 미션을 하나 생성해 OFFERED 상태로 저장한다.
     *
     * 진행 중인 미션이 이미 있으면 새 미션을 만들지 않는다.
     * 이 검사는 유저 기준으로 수행하고, characterId는 새 미션 row에 제안 캐릭터로 저장한다.
     *
     * 미션 row는 먼저 template fallback 문구로 짧게 저장하고, 그 뒤 트랜잭션 밖에서 ai 모듈을 호출한다.
     * AI 호출이 성공하면 다시 짧은 트랜잭션을 열어 캐릭터 말투 문구와 aiGenerationId를 반영한다.
     */
    public CreateNextMissionResponse createNextMission(Long userId, Long characterId, Long lastMissionId) {
        LocalDate today = LocalDate.now(clock);
        validateMissionCreatable(userId, today);

        MissionCreationContext context = Objects.requireNonNull(transactionTemplate.execute(
                status -> createMissionWithFallback(userId, characterId, today)
        ));
        Optional<AiMissionTextResult> generatedText = generateMissionText(context);
        UserMission mission = Objects.requireNonNull(transactionTemplate.execute(
                status -> finalizeMissionCreation(userId, context.missionId(), generatedText)
        ));

        return CreateNextMissionResponse.newBuilder()
                .setMission(toProtoMission(mission))
                .build();
    }

    /**
     * 미션 생성 전 빠르게 막을 수 있는 조건을 확인한다.
     *
     * 이 검사는 외부 gRPC 호출 전에 실패를 반환하기 위한 1차 방어이고,
     * 실제 저장 트랜잭션 안에서 같은 검사를 한 번 더 수행한다.
     */
    private void validateMissionCreatable(Long userId, LocalDate today) {
        if (userMissionRepository.existsByUserIdAndMissionDateAndStatusIn(userId, today, ACTIVE_STATUSES)) {
            throw new MissionException(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        }

        long todayOfferCount = userMissionRepository.countByUserIdAndMissionDate(userId, today);
        if (todayOfferCount >= DAILY_MISSION_OFFER_LIMIT) {
            throw new MissionException(MissionErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
        }
    }

    /**
     * seed template 기준의 fallback 미션을 먼저 저장한다.
     *
     * 여기서 missionId가 생기므로, 이후 AI requestId를 안정적으로 만들 수 있다.
     * 완료 질문 row도 fallback 질문으로 먼저 만들어 두고, AI 성공 시 질문 텍스트만 교체한다.
     */
    private MissionCreationContext createMissionWithFallback(Long userId, Long characterId, LocalDate today) {
        validateMissionCreatable(userId, today);

        LocalDateTime now = LocalDateTime.now(clock);
        MissionTemplate template = selectNextTemplate(userId, today);
        int nextStackOrder = userMissionRepository.findMaxStackOrder(userId, today) + 1;
        UserMission userMission = UserMission.offerFromTemplate(
                userId,
                characterId,
                today,
                nextStackOrder,
                template,
                now
        );

        try {
            UserMission savedMission = userMissionRepository.saveAndFlush(userMission);
            missionCompletionAnswerRepository.save(MissionCompletionAnswer.start(
                    savedMission.getId(),
                    savedMission.getUserId(),
                    template.getFallbackQuestion()
            ));
            return MissionCreationContext.from(savedMission, template, missionTextRequestId(savedMission));
        } catch (DataIntegrityViolationException e) {
            // 동시에 미션 생성 요청이 들어오면 애플리케이션의 exists 검사만으로는 중복 생성이 가능하다.
            // DB partial unique index가 마지막으로 막아주고, 여기서는 도메인 예외로 변환한다.
            throw new MissionException(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        }
    }

    /**
     * character/ai 모듈을 트랜잭션 밖에서 호출한다.
     *
     * character나 ai 모듈 호출이 실패하면 Optional.empty()를 반환해 fallback 문구로 미션 생성을 마무리한다.
     */
    private Optional<AiMissionTextResult> generateMissionText(MissionCreationContext context) {
        return characterProfileClient.findActiveCharacterTypeCode(context.userId(), context.characterId())
                .flatMap(characterType -> aiMissionTextClient.generateMissionTexts(new AiMissionTextRequest(
                        context.userId(),
                        context.characterId(),
                        characterType,
                        context.missionTemplateId(),
                        context.baseTitle(),
                        context.baseDescription(),
                        context.category(),
                        context.difficulty(),
                        context.fallbackCharacterMessage(),
                        context.fallbackQuestion(),
                        context.fallbackCompletionResponse(),
                        EMPTY_JSON_OBJECT,
                        EMPTY_JSON_OBJECT,
                        context.aiRequestId()
                )));
    }

    /**
     * AI 결과가 있으면 미션 문구와 완료 질문을 교체하고, 없으면 fallback 상태 그대로 확정한다.
     *
     * MISSION_OFFERED 이벤트는 최종 문구가 정해진 뒤 발행해 event-log metadata에 aiGenerationId를 담을 수 있게 한다.
     */
    private UserMission finalizeMissionCreation(
            Long userId,
            Long missionId,
            Optional<AiMissionTextResult> generatedText
    ) {
        UserMission mission = findOwnedMissionForUpdate(userId, missionId);
        MissionCompletionAnswer answer = missionCompletionAnswerRepository.findByMissionId(mission.getId())
                .orElseGet(() -> missionCompletionAnswerRepository.save(MissionCompletionAnswer.start(
                        mission.getId(),
                        mission.getUserId(),
                        resolveCompletionQuestion(mission)
                )));

        generatedText.ifPresent(text -> {
            mission.applyGeneratedTexts(
                    text.aiGenerationId(),
                    text.characterMessage(),
                    text.completionCharacterResponse()
            );
            answer.replaceQuestion(text.completionQuestion());
        });

        eventPublisher.publishEvent(MissionEventLogEvent.missionOffered(
                mission,
                toOccurredAt(mission.getOfferedAt())
        ));
        return mission;
    }

    /**
     * 유저가 제안받은 미션을 거절 처리한다.
     *
     * 거절도 소유권 기준은 userId + missionId다.
     * characterId를 조건에 넣으면 같은 유저의 현재 미션을 다른 캐릭터 화면에서 처리할 때 실패할 수 있다.
     */
    @Transactional
    public RejectMissionResponse rejectMission(Long userId, Long missionId) {
        UserMission mission = userMissionRepository.findByIdAndUserId(missionId, userId)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));

        if (!mission.isOffered()) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        mission.reject(now);
        eventPublisher.publishEvent(MissionEventLogEvent.missionRejected(mission, toOccurredAt(now)));

        return RejectMissionResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setRejectedAt(formatDateTime(mission.getRejectedAt()))
                .setCharacterMessage(REJECTION_CHARACTER_MESSAGE)
                .build();
    }

    /**
     * OFFERED 상태의 미션을 완료 질문 답변 중인 ANSWERING 상태로 전환한다.
     *
     * 모바일 중복 클릭이나 네트워크 재시도를 고려해 이미 ANSWERING이면 기존 질문을 다시 내려준다.
     */
    @Transactional
    public StartCompletionSessionResponse startCompletionSession(Long userId, Long missionId) {
        UserMission mission = findOwnedMissionForUpdate(userId, missionId);

        if (mission.isCompleted()) {
            throw new MissionException(MissionErrorCode.MISSION_ALREADY_COMPLETED);
        }

        if (!mission.isOffered() && !mission.isAnswering()) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }

        boolean shouldPublishSessionStarted = mission.isOffered();
        LocalDateTime now = LocalDateTime.now(clock);
        if (shouldPublishSessionStarted) {
            mission.startAnswering(now);
        }

        MissionCompletionAnswer answer = findOrCreateCompletionAnswer(mission);
        if (shouldPublishSessionStarted) {
            eventPublisher.publishEvent(MissionEventLogEvent.completionSessionStarted(
                    mission,
                    answer,
                    toOccurredAt(now)
            ));
        }

        return StartCompletionSessionResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setQuestion(toProtoQuestion(answer))
                .build();
    }

    /**
     * 완료 질문에 대한 텍스트 답변을 저장하고 미션을 COMPLETED 상태로 전환한다.
     *
     * mission DB 쓰기와 wallet gRPC 호출을 한 트랜잭션에 넣지 않는다.
     * 답변 저장/상태 전환은 짧은 mission 트랜잭션으로 끝내고, 별조각 지급은 트랜잭션 밖에서 호출한다.
     * wallet 지급 요청은 mission_reward_outbox를 통해 남기고, 성공하면 dispatcher가 보상 지급 완료 marker까지 저장한다.
     */
    public SubmitCompletionAnswerResponse submitCompletionAnswer(Long userId, Long missionId, String answerText) {
        String normalizedAnswer = validateAndNormalizeAnswer(answerText);
        MissionCompletionContext context = Objects.requireNonNull(transactionTemplate.execute(
                status -> completeMissionInTransaction(userId, missionId, normalizedAnswer)
        ));

        WalletRewardResult rewardResult = resolveReward(userId, context);
        return toCompletionResponse(context.mission(), context.answer(), rewardResult.starPiece());
    }

    /**
     * 완료 답변 저장과 미션 상태 전환만 담당하는 짧은 mission DB 트랜잭션이다.
     *
     * 같은 완료 API가 재시도되면 저장된 답변이 같은지 확인한 뒤 기존 완료 결과를 재사용한다.
     * 저장된 답변과 다른 답변으로 다시 제출하려는 경우에는 답변 수정을 허용하지 않는다.
     */
    private MissionCompletionContext completeMissionInTransaction(Long userId, Long missionId, String normalizedAnswer) {
        UserMission mission = findOwnedMissionForUpdate(userId, missionId);
        String rewardIdempotencyKey = rewardIdempotencyKey(mission.getId());

        if (mission.isCompleted()) {
            MissionCompletionAnswer answer = missionCompletionAnswerRepository.findByMissionId(mission.getId())
                    .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_INVALID_STATUS));
            if (!answer.hasSameAnswer(normalizedAnswer)) {
                throw new MissionException(MissionErrorCode.MISSION_ALREADY_COMPLETED);
            }

            if (mission.isRewardPaid()) {
                return new MissionCompletionContext(mission, answer, true, rewardIdempotencyKey, null);
            }

            MissionRewardOutbox outbox = ensureRewardOutbox(mission, rewardIdempotencyKey, LocalDateTime.now(clock));
            return new MissionCompletionContext(mission, answer, false, rewardIdempotencyKey, outbox.getId());
        }

        if (!mission.isAnswering()) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }

        MissionCompletionAnswer answer = missionCompletionAnswerRepository.findByMissionId(mission.getId())
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_INVALID_STATUS));

        if (answer.isAnswered()) {
            throw new MissionException(MissionErrorCode.MISSION_ALREADY_COMPLETED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        answer.submit(normalizedAnswer, now);
        mission.complete(now);
        eventPublisher.publishEvent(MissionEventLogEvent.missionCompleted(mission, answer, toOccurredAt(now)));
        MissionRewardOutbox outbox = ensureRewardOutbox(mission, rewardIdempotencyKey, now);

        return new MissionCompletionContext(mission, answer, false, rewardIdempotencyKey, outbox.getId());
    }

    /**
     * wallet 보상 지급 결과를 구한다.
     *
     * 이미 user_missions.idempotency_key가 있으면 보상 지급이 끝난 것으로 보고 새 거래를 만들지 않는다.
     * marker가 없으면 같은 MISSION_REWARD:{missionId} 키로 outbox를 즉시 발송해 중복 지급을 방어한다.
     */
    private WalletRewardResult resolveReward(Long userId, MissionCompletionContext context) {
        if (context.rewardAlreadyPaid()) {
            return new WalletRewardResult(walletRewardClient.getWalletStarPiece(userId), null);
        }

        return missionRewardDispatcher.dispatchNow(context.rewardOutboxId());
    }

    private MissionRewardOutbox ensureRewardOutbox(
            UserMission mission,
            String rewardIdempotencyKey,
            LocalDateTime nextAttemptAt
    ) {
        return missionRewardOutboxRepository.findByMissionId(mission.getId())
                .orElseGet(() -> missionRewardOutboxRepository.save(MissionRewardOutbox.pending(
                        mission,
                        rewardIdempotencyKey,
                        nextAttemptAt
                )));
    }

    /**
     * 오늘 아직 사용하지 않은 활성 미션 템플릿을 하나 고른다.
     *
     * AI가 미션 제목/보상/카테고리를 임의 생성하지 않도록 seed template을 기준으로 선택한다.
     * 같은 날 같은 템플릿이 반복 제안되는 느낌을 줄이기 위해 오늘 사용한 template id는 제외한다.
     * 후보 선택 순서는 id 오름차순이 아니라 날짜 기준 랜덤 정책에 맡긴다.
     */
    private MissionTemplate selectNextTemplate(Long userId, LocalDate missionDate) {
        List<Long> usedTemplateIds = userMissionRepository.findMissionTemplateIdsByUserIdAndMissionDate(userId, missionDate);
        Set<Long> usedTemplateIdSet = Set.copyOf(usedTemplateIds);
        List<MissionTemplate> activeTemplates = missionTemplateRepository.findByActiveTrueOrderByIdAsc();

        return missionTemplateSelector.selectNextTemplate(
                userId,
                missionDate,
                activeTemplates,
                usedTemplateIdSet
        );
    }

    private UserMission findOwnedMissionForUpdate(Long userId, Long missionId) {
        return userMissionRepository.findByIdAndUserIdForUpdate(missionId, userId)
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
    }

    private MissionCompletionAnswer findOrCreateCompletionAnswer(UserMission mission) {
        return missionCompletionAnswerRepository.findByMissionId(mission.getId())
                .orElseGet(() -> missionCompletionAnswerRepository.save(MissionCompletionAnswer.start(
                        mission.getId(),
                        mission.getUserId(),
                        resolveCompletionQuestion(mission)
                )));
    }

    private String resolveCompletionQuestion(UserMission mission) {
        if (mission.getMissionTemplateId() == null) {
            return DEFAULT_COMPLETION_QUESTION;
        }

        return missionTemplateRepository.findById(mission.getMissionTemplateId())
                .map(MissionTemplate::getFallbackQuestion)
                .orElse(DEFAULT_COMPLETION_QUESTION);
    }

    private String validateAndNormalizeAnswer(String answerText) {
        if (answerText == null) {
            throw new MissionException(MissionErrorCode.MISSION_ANSWER_INVALID);
        }

        String normalizedAnswer = answerText.trim();
        if (normalizedAnswer.length() < COMPLETION_ANSWER_MIN_LENGTH
                || normalizedAnswer.length() > COMPLETION_ANSWER_MAX_LENGTH) {
            throw new MissionException(MissionErrorCode.MISSION_ANSWER_INVALID);
        }

        return normalizedAnswer;
    }

    private String rewardIdempotencyKey(Long missionId) {
        return MISSION_REWARD_IDEMPOTENCY_KEY_PREFIX + missionId;
    }

    private String missionTextRequestId(UserMission mission) {
        return MISSION_TEXT_REQUEST_ID_PREFIX + sha256(String.join(
                "|",
                String.valueOf(mission.getUserId()),
                String.valueOf(mission.getId()),
                String.valueOf(mission.getMissionTemplateId())
        ));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new MissionException(MissionErrorCode.MISSION_INVALID_STATUS);
        }
    }

    private SubmitCompletionAnswerResponse toCompletionResponse(
            UserMission mission,
            MissionCompletionAnswer answer,
            int walletStarPiece
    ) {
        return SubmitCompletionAnswerResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setAnswer(toProtoAnswer(answer))
                .setReward(MissionReward.newBuilder()
                        .setStarPiece(mission.getRewardStarPiece())
                        .setAffection(0)
                        .build())
                .setWallet(WalletSnapshot.newBuilder()
                        .setStarPiece(walletStarPiece)
                        .build())
                .setCharacterMessage(mission.getCompletionCharacterResponse())
                .build();
    }

    /**
     * JPA Entity를 gRPC 응답용 Mission message로 변환한다.
     *
     * Entity를 외부 계약에 직접 노출하지 않고, proto 계약에 맞는 값만 골라 내려준다.
     */
    private com.p5laris.proto.mission.v1.Mission toProtoMission(UserMission mission) {
        return com.p5laris.proto.mission.v1.Mission.newBuilder()
                .setId(mission.getId())
                .setMissionDate(mission.getMissionDate().toString())
                .setStackOrder(mission.getStackOrder())
                .setTitle(mission.getTitle())
                .setDescription(mission.getDescription())
                .setCharacterMessage(mission.getCharacterMessage())
                .setCategory(toProtoCategory(mission))
                .setDifficulty(toProtoDifficulty(mission))
                .setRewardStarPiece(mission.getRewardStarPiece())
                .setStatus(toProtoStatus(mission.getStatus()))
                .build();
    }

    /**
     * 오늘 미션 히스토리 목록에 필요한 최소 필드만 proto로 변환한다.
     *
     * 완료 답변 전문은 민감할 수 있어 히스토리 응답에 포함하지 않는다.
     */
    private TodayMission toProtoTodayMission(UserMission mission) {
        return TodayMission.newBuilder()
                .setId(mission.getId())
                .setStackOrder(mission.getStackOrder())
                .setTitle(mission.getTitle())
                .setCategory(toProtoCategory(mission))
                .setDifficulty(toProtoDifficulty(mission))
                .setRewardStarPiece(mission.getRewardStarPiece())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setCharacterMessage(mission.getCharacterMessage())
                .setCreatedAt(formatDateTime(mission.getCreatedAt()))
                .setCompletedAt(formatDateTime(mission.getCompletedAt()))
                .setRejectedAt(formatDateTime(mission.getRejectedAt()))
                .build();
    }

    private int countByStatus(List<UserMission> missions, UserMissionStatus status) {
        return (int) missions.stream()
                .filter(mission -> mission.getStatus() == status)
                .count();
    }

    private Optional<Long> findCurrentMissionId(List<UserMission> missions) {
        return missions.stream()
                .filter(mission -> ACTIVE_STATUSES.contains(mission.getStatus()))
                .map(UserMission::getId)
                .findFirst();
    }

    private CompletionQuestion toProtoQuestion(MissionCompletionAnswer answer) {
        return CompletionQuestion.newBuilder()
                .setId(answer.getId())
                .setText(answer.getQuestionText())
                .setInputType(CompletionInputType.COMPLETION_INPUT_TYPE_TEXT)
                .setMinLength(COMPLETION_ANSWER_MIN_LENGTH)
                .setMaxLength(COMPLETION_ANSWER_MAX_LENGTH)
                .build();
    }

    private CompletionAnswer toProtoAnswer(MissionCompletionAnswer answer) {
        return CompletionAnswer.newBuilder()
                .setText(answer.getAnswerText())
                .setAnsweredAt(formatDateTime(answer.getAnsweredAt()))
                .build();
    }

    // DB enum 이름에 proto enum prefix를 붙여 MissionCategory proto enum으로 변환한다.
    private MissionCategory toProtoCategory(UserMission mission) {
        return MissionCategory.valueOf("MISSION_CATEGORY_" + mission.getCategory().name());
    }

    // DB enum 이름에 proto enum prefix를 붙여 MissionDifficulty proto enum으로 변환한다.
    private MissionDifficulty toProtoDifficulty(UserMission mission) {
        return MissionDifficulty.valueOf("MISSION_DIFFICULTY_" + mission.getDifficulty().name());
    }

    // DB enum 이름에 proto enum prefix를 붙여 MissionStatus proto enum으로 변환한다.
    private MissionStatus toProtoStatus(UserMissionStatus status) {
        return MissionStatus.valueOf("MISSION_STATUS_" + status.name());
    }

    // proto에는 LocalDateTime 타입을 직접 쓰지 않으므로 ISO 문자열로 변환한다.
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private OffsetDateTime toOccurredAt(LocalDateTime dateTime) {
        return dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }

    /**
     * 완료 처리 트랜잭션이 끝난 뒤 wallet 호출에 필요한 미션/답변 스냅샷이다.
     *
     * rewardAlreadyPaid가 true면 이미 mission row에 보상 지급 marker가 있으므로 wallet 적립을 다시 호출하지 않는다.
     * rewardOutboxId는 marker가 없을 때 dispatcher가 즉시 발송할 outbox row id다.
     */
    private record MissionCompletionContext(
            UserMission mission,
            MissionCompletionAnswer answer,
            boolean rewardAlreadyPaid,
            String rewardIdempotencyKey,
            Long rewardOutboxId
    ) {
    }

    /**
     * fallback 미션 저장 후 AI 문구 생성을 요청하는 데 필요한 값만 담은 스냅샷이다.
     *
     * 이 record는 트랜잭션 밖에서 사용되므로 JPA Entity 대신 문자열/ID 값만 들고 간다.
     */
    private record MissionCreationContext(
            Long missionId,
            Long userId,
            Long characterId,
            Long missionTemplateId,
            String baseTitle,
            String baseDescription,
            String category,
            String difficulty,
            String fallbackCharacterMessage,
            String fallbackQuestion,
            String fallbackCompletionResponse,
            String aiRequestId
    ) {

        private static MissionCreationContext from(
                UserMission mission,
                MissionTemplate template,
                String aiRequestId
        ) {
            return new MissionCreationContext(
                    mission.getId(),
                    mission.getUserId(),
                    mission.getCharacterId(),
                    template.getId(),
                    template.getBaseTitle(),
                    template.getBaseDescription(),
                    template.getCategory().name(),
                    template.getDifficulty().name(),
                    template.getFallbackCharacterMessage(),
                    template.getFallbackQuestion(),
                    template.getFallbackCompletionResponse(),
                    aiRequestId
            );
        }
    }
}
