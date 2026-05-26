package p5laris.mission.domain.application;

import org.springframework.stereotype.Component;
import p5laris.mission.domain.domain.entity.MissionTemplate;
import p5laris.mission.domain.exception.MissionErrorCode;
import p5laris.mission.domain.exception.MissionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 오늘 제안할 미션 템플릿을 고르는 정책 클래스다.
 *
 * 완전한 난수를 쓰면 같은 요청을 재시도할 때 다른 미션이 선택될 수 있다.
 * 그래서 userId, 날짜, templateId를 seed처럼 사용해 "하루 단위로만 바뀌는 랜덤 순서"를 만든다.
 */
@Component
public class MissionTemplateSelector {

    // 정렬용 해시가 우연히 다른 기능의 해시와 섞이지 않도록 미션 템플릿 선택 전용 salt를 둔다.
    private static final String DAILY_RANDOM_SALT = "MISSION_TEMPLATE_DAILY_RANDOM_V1";

    public MissionTemplate selectNextTemplate(
            Long userId,
            LocalDate missionDate,
            List<MissionTemplate> activeTemplates,
            Set<Long> usedTemplateIds
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(missionDate, "missionDate must not be null");
        Objects.requireNonNull(activeTemplates, "activeTemplates must not be null");

        Set<Long> usedIds = usedTemplateIds == null ? Set.of() : usedTemplateIds;

        return activeTemplates.stream()
                .filter(template -> template.getId() != null)
                .filter(template -> !usedIds.contains(template.getId()))
                .min(Comparator
                        .comparing((MissionTemplate template) -> dailyRandomKey(userId, missionDate, template.getId()))
                        .thenComparing(MissionTemplate::getId))
                .orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_TEMPLATE_NOT_FOUND));
    }

    private String dailyRandomKey(Long userId, LocalDate missionDate, Long templateId) {
        // 정렬 키에 templateId까지 넣어 같은 날의 후보 템플릿들이 유저별로 다른 순서가 되게 한다.
        return sha256(String.join(
                "|",
                DAILY_RANDOM_SALT,
                String.valueOf(userId),
                missionDate.toString(),
                String.valueOf(templateId)
        ));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }
}
