package p5laris.mission.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.mission.domain.domain.entity.UserMemory;
import p5laris.mission.domain.domain.enums.UserMemorySourceType;
import p5laris.mission.domain.domain.enums.UserMemoryType;

import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    Optional<UserMemory> findBySourceTypeAndSourceIdAndMemoryType(
            UserMemorySourceType sourceType,
            Long sourceId,
            UserMemoryType memoryType
    );
}
