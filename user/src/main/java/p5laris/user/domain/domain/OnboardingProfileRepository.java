package p5laris.user.domain.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OnboardingProfileRepository extends JpaRepository<OnboardingProfile, Long> {
    Optional<OnboardingProfile> findByUserId(Long userId);
}
