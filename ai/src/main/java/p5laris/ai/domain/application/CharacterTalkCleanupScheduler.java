package p5laris.ai.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 별친구 대화 원문 보관 기간을 제한하는 정리 작업이다.
 *
 * 세션 통계와 요약 기억은 남기되, 원문 메시지는 짧게 보관해 개인정보 노출 범위를 줄인다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CharacterTalkCleanupScheduler {

    private final CharacterTalkHistoryService characterTalkHistoryService;

    @Scheduled(fixedDelayString = "${ai.character-talk.cleanup-fixed-delay-ms:3600000}")
    public void cleanup() {
        try {
            characterTalkHistoryService.cleanupExpiredData();
        } catch (Exception e) {
            log.warn("별친구 대화 보관 정책 정리 실패. 예외클래스={}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "${ai.character-talk.daily-summary-cron:0 5 0 * * *}", zone = "Asia/Seoul")
    public void summarizeDailyBoundarySessions() {
        try {
            characterTalkHistoryService.summarizeDailyBoundarySessions();
        } catch (Exception e) {
            log.warn("별친구 대화 일일 기억 생성 실패. 예외클래스={}", e.getClass().getSimpleName());
        }
    }
}
