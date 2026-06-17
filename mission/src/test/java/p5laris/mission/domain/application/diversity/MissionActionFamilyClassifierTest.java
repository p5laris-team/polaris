package p5laris.mission.domain.application.diversity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissionActionFamilyClassifierTest {

    @Test
    void 손목과_발목은_목어깨_행동군으로_오판하지_않는다() {
        assertThat(MissionActionFamilyClassifier.classify(
                "손목 10초 돌리기",
                "손목을 천천히 10초 동안 돌려보세요."
        )).isEqualTo(MissionActionFamily.BODY_STRETCH);

        assertThat(MissionActionFamilyClassifier.classify(
                "발목 10초 돌리기",
                "발목을 천천히 10초 동안 돌려보세요."
        )).isEqualTo(MissionActionFamily.BODY_STRETCH);
    }

    @Test
    void 목과_어깨는_목어깨_행동군으로_분류한다() {
        assertThat(MissionActionFamilyClassifier.classify(
                "목과 어깨 가볍게 돌리기",
                "목을 천천히 좌우로, 어깨를 앞뒤로 가볍게 돌려보세요."
        )).isEqualTo(MissionActionFamily.NECK_SHOULDER_STRETCH);
    }

    @Test
    void 잠깐이라는_표현만으로_수면_준비로_오판하지_않는다() {
        assertThat(MissionActionFamilyClassifier.classify(
                "복도나 마당 한 번 보기",
                "복도, 마당, 베란다처럼 방 밖 공간을 잠깐 바라보세요."
        )).isEqualTo(MissionActionFamily.UNKNOWN);
    }

    @Test
    void 보내는_응원_문장은_기록보다_가벼운_연락으로_분류한다() {
        assertThat(MissionActionFamilyClassifier.classify(
                "응원 문장 하나 보내기",
                "응원이 필요한 사람에게 짧은 응원 문장을 보내보세요."
        )).isEqualTo(MissionActionFamily.SOCIAL_CONTACT);
    }
}
