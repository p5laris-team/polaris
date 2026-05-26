package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.domain.entity.UserCharacter;
import p5laris.character.domain.domain.enums.CharacterMood;
import p5laris.character.domain.domain.repository.UserCharacterRepository;
import p5laris.character.domain.infrastructure.grpc.NotificationPushClient;

/**
 * 활성 캐릭터의 시간 기반 상태 감소를 점검하고, 돌봄이 필요한 상태만 알림으로 연결한다.
 *
 * 상태값 저장은 character 모듈 책임이고, 실제 푸시 발송 여부는 notification 모듈 정책에 맡긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterStateNotificationService {

    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int MAX_BATCH_SIZE = 1_000;

    private final UserCharacterRepository userCharacterRepository;
    private final NotificationPushClient notificationPushClient;

    /**
     * active 캐릭터를 페이지 단위로 조회해 밀린 상태 감소를 반영하고 필요한 알림을 요청한다.
     *
     * 알림 실패는 상태 감소 저장을 막으면 안 되므로 캐릭터 단위로 잡아서 warn 로그만 남긴다.
     */
    @Transactional
    public int dispatchDueStateNotifications(int batchSize) {
        int normalizedBatchSize = normalizeBatchSize(batchSize);
        int pageNumber = 0;
        int requestedCount = 0;

        Page<UserCharacter> page;
        do {
            page = userCharacterRepository.findByActiveTrue(
                    PageRequest.of(pageNumber, normalizedBatchSize, Sort.by("id").ascending())
            );

            for (UserCharacter character : page.getContent()) {
                if (requestNotificationIfNeeded(character)) {
                    requestedCount++;
                }
            }

            pageNumber++;
        } while (page.hasNext());

        return requestedCount;
    }

    private boolean requestNotificationIfNeeded(UserCharacter character) {
        boolean decreased = character.calculateTimeBasedStatDecrease();
        if (!decreased) {
            return false;
        }

        CharacterMood mood = character.calculateMood();
        if (mood == CharacterMood.IDLE || mood == CharacterMood.HAPPY || mood == CharacterMood.SLEEPY) {
            return false;
        }

        if (character.getUserId() == null || character.getId() == null) {
            log.warn("캐릭터 상태 알림 생략. userId 또는 characterId가 없습니다. userId={}, characterId={}",
                    character.getUserId(), character.getId());
            return false;
        }

        try {
            notificationPushClient.sendCharacterStateNotification(
                    character.getUserId(),
                    character.getId(),
                    character.getName(),
                    mood
            );
            return true;
        } catch (Exception e) {
            log.warn("캐릭터 상태 알림 요청 실패. userId={}, characterId={}, mood={}",
                    character.getUserId(), character.getId(), mood, e);
            return false;
        }
    }

    private int normalizeBatchSize(int batchSize) {
        if (batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }

        return Math.min(batchSize, MAX_BATCH_SIZE);
    }
}
