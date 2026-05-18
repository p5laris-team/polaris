package p5laris.user.domain.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import p5laris.user.domain.domain.dto.AnswerOption;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum OnboardingQuestion {
    LIVING_TYPE("지금 생활 환경은 어떤가요?", List.of(
            new AnswerOption("LIVING_ALONE", "혼자 살아요"),
            new AnswerOption("WITH_FAMILY", "가족과 살아요"),
            new AnswerOption("WITH_ROOMMATE", "룸메이트와 살아요"),
            new AnswerOption("OTHER", "기타")
    )),
    WAKE_UP_TIME("보통 몇 시쯤 일어나나요?", List.of(
            new AnswerOption("06:00~08:00", "6~8시"),
            new AnswerOption("08:00~10:00", "8~10시"),
            new AnswerOption("AFTER_10:00", "10시 이후"),
            new AnswerOption("UNKNOWN", "일정하지 않아요")
    )),
    SLEEP_TIME("보통 몇 시쯤 자나요?", List.of(
            new AnswerOption("22:00~23:00", "10~11시"),
            new AnswerOption("23:00~00:00", "11~12시"),
            new AnswerOption("AFTER_00:00", "12시 이후"),
            new AnswerOption("UNKNOWN", "일정하지 않아요")
    )),
    PREFERRED_MISSION_TIME("원하는 미션 시간이 있나요?", List.of(
            new AnswerOption("MORNING", "오전"),
            new AnswerOption("AFTERNOON", "오후"),
            new AnswerOption("EVENING", "새벽"),
            new AnswerOption("NIGHT", "저녁 이후"),
            new AnswerOption("ANYTIME", "아무때나")
    )),
    ROUTINE_GOAL("지금 만들고 싶은 루틴은 무엇인가요?", List.of(
            new AnswerOption("WAKE_UP", "일어나기"),
            new AnswerOption("CLEAN_ROOM", "방 정리"),
            new AnswerOption("GO_OUT", "짧은 외출"),
            new AnswerOption("SELF_CARE", "자기 돌봄"),
            new AnswerOption("STUDY", "공부/집중"),
            new AnswerOption("LIGHT_ACTIVITY", "가벼운 활동")
    )),
    MISSION_INTENSITY("미션은 어느 정도가 좋아요?", List.of(
            new AnswerOption("VERY_LIGHT", "정말 가벼운 것"),
            new AnswerOption("LIGHT", "5분 안에 할 수 있는 것"),
            new AnswerOption("NORMAL", "조금 움직이는 것")
    )),
    ACTIVITY_PREFERENCE("실내/실외 중 무엇이 편한가요?", List.of(
            new AnswerOption("INDOOR", "실내"),
            new AnswerOption("OUTDOOR", "실외"),
            new AnswerOption("BOTH", "둘 다 괜찮아요")
    ));

    private final String content;
    private final List<AnswerOption> options;
}
