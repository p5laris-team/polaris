package p5laris.mission.domain.application;

import com.p5laris.proto.mission.v1.CreateNextMissionResponse;
import com.p5laris.proto.mission.v1.GetCurrentMissionResponse;
import com.p5laris.proto.mission.v1.MissionCategory;
import com.p5laris.proto.mission.v1.MissionDifficulty;
import com.p5laris.proto.mission.v1.MissionStatus;
import com.p5laris.proto.mission.v1.RejectMissionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.domain.entity.UserMission;
import p5laris.mission.domain.domain.enums.UserMissionStatus;
import p5laris.mission.domain.domain.repository.MissionTemplateRepository;
import p5laris.mission.domain.domain.repository.UserMissionRepository;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MissionService {

    private static final int DAILY_MISSION_OFFER_LIMIT = 15;
    private static final String REJECTION_CHARACTER_MESSAGE = "괜찮아요. 다른 별을 찾아볼게요.";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Set<UserMissionStatus> ACTIVE_STATUSES = EnumSet.of(
            UserMissionStatus.OFFERED,
            UserMissionStatus.ANSWERING
    );

    private final MissionTemplateRepository missionTemplateRepository;
    private final UserMissionRepository userMissionRepository;
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
     * 다음 미션을 하나 생성해 OFFERED 상태로 저장한다.
     *
     * 진행 중인 미션이 이미 있으면 새 미션을 만들지 않는다.
     * 이 검사는 유저 기준으로 수행하고, characterId는 새 미션 row에 제안 캐릭터로 저장한다.
     */
    @Transactional
    public CreateNextMissionResponse createNextMission(Long userId, Long characterId, Long lastMissionId) {
        LocalDate today = LocalDate.now(clock);

        if (userMissionRepository.existsByUserIdAndMissionDateAndStatusIn(userId, today, ACTIVE_STATUSES)) {
            throw new MissionException(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        }

        long todayOfferCount = userMissionRepository.countByUserIdAndMissionDate(userId, today);
        if (todayOfferCount >= DAILY_MISSION_OFFER_LIMIT) {
            throw new MissionException(MissionErrorCode.MISSION_DAILY_LIMIT_EXCEEDED);
        }

        MissionTemplate template = selectNextTemplate(userId, today);
        int nextStackOrder = userMissionRepository.findMaxStackOrder(userId, today) + 1;
        UserMission userMission = UserMission.offerFromTemplate(
                userId,
                characterId,
                today,
                nextStackOrder,
                template,
                LocalDateTime.now(clock)
        );

        try {
            UserMission savedMission = userMissionRepository.saveAndFlush(userMission);
            return CreateNextMissionResponse.newBuilder()
                    .setMission(toProtoMission(savedMission))
                    .build();
        } catch (DataIntegrityViolationException e) {
            // 동시에 미션 생성 요청이 들어오면 애플리케이션의 exists 검사만으로는 중복 생성이 가능하다.
            // DB partial unique index가 마지막으로 막아주고, 여기서는 도메인 예외로 변환한다.
            throw new MissionException(MissionErrorCode.MISSION_ACTIVE_ALREADY_EXISTS);
        }
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

        mission.reject(LocalDateTime.now(clock));

        return RejectMissionResponse.newBuilder()
                .setMissionId(mission.getId())
                .setStatus(toProtoStatus(mission.getStatus()))
                .setRejectedAt(formatDateTime(mission.getRejectedAt()))
                .setCharacterMessage(REJECTION_CHARACTER_MESSAGE)
                .build();
    }

    /**
     * 오늘 아직 사용하지 않은 활성 미션 템플릿을 하나 고른다.
     *
     * AI가 미션 제목/보상/카테고리를 임의 생성하지 않도록 seed template을 기준으로 선택한다.
     * 같은 날 같은 템플릿이 반복 제안되는 느낌을 줄이기 위해 오늘 사용한 template id는 제외한다.
     */
    private MissionTemplate selectNextTemplate(Long userId, LocalDate missionDate) {
        List<Long> usedTemplateIds = userMissionRepository.findMissionTemplateIdsByUserIdAndMissionDate(userId, missionDate);
        Set<Long> usedTemplateIdSet = new HashSet<>(usedTemplateIds);

        return missionTemplateRepository.findByActiveTrueOrderByIdAsc().stream()
                .filter(template -> !usedTemplateIdSet.contains(template.getId()))
                .findFirst()
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_TEMPLATE_NOT_FOUND));
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
}
