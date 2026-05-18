package p5laris.user.domain.application;

import com.p5laris.proto.user.v1.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.user.domain.domain.repository.UserRepository;
import p5laris.user.domain.exception.UserErrorCode;
import p5laris.user.domain.exception.UserException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getUser(Long userId) {
        p5laris.user.domain.domain.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        return User.newBuilder()
                .setId(user.getId())
                .setEmail(user.getEmail())
                .setNickname(user.getNickname())
                .setProvider(user.getProvider())
                .setRole(user.getRole())
                .setStatus(user.getStatus())
                .build();
    }
}
