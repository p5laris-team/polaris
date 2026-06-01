package p5laris.mission.domain.application.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import p5laris.mission.domain.infrastructure.config.MissionWeatherProperties;

import java.util.Optional;

/**
 * 사용자 지역 선택 기능이 들어오기 전까지 운영 기본 격자 좌표를 날씨 조회 기준으로 사용한다.
 *
 * 이후 user 모듈에 사용자 선택 지역이 저장되면 같은 WeatherLocation 계약으로 교체할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class DefaultWeatherLocationResolver implements WeatherLocationResolver {

    private final MissionWeatherProperties properties;

    @Override
    public Optional<WeatherLocation> resolve(Long userId) {
        if (!properties.hasDefaultLocation()) {
            return Optional.empty();
        }

        return Optional.of(new WeatherLocation(
                properties.getDefaultNx(),
                properties.getDefaultNy(),
                properties.getDefaultLocationLabel().trim(),
                WeatherLocationSource.SERVICE_DEFAULT
        ));
    }
}
