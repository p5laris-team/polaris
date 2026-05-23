package p5laris.notification.domain.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.notification.domain.domain.entity.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 ID만으로 찾지 않고 userId를 함께 확인해 다른 유저의 알림 읽음 처리를 막는다.
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // cursor가 없고 read 필터도 없을 때 최신 알림 목록을 조회한다.
    List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    // cursor가 있고 read 필터가 없을 때 cursor 이전 알림 목록을 조회한다.
    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long id, Pageable pageable);

    // cursor가 없고 read 필터가 있을 때 최신 알림 목록을 조회한다.
    List<Notification> findByUserIdAndReadOrderByIdDesc(Long userId, boolean read, Pageable pageable);

    // cursor와 read 필터가 모두 있을 때 cursor 이전 알림 목록을 조회한다.
    List<Notification> findByUserIdAndReadAndIdLessThanOrderByIdDesc(
            Long userId,
            boolean read,
            Long id,
            Pageable pageable
    );

    // 홈 화면 unreadCount 표시용이다.
    long countByUserIdAndReadFalse(Long userId);
}