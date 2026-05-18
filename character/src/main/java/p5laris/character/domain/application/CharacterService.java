package p5laris.character.domain.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.character.domain.application.dto.CharacterTypeResponse;
import p5laris.character.domain.domain.repository.CharacterTypeRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterTypeRepository characterTypeRepository;

    /**
     * 선택 가능한 캐릭터 타입 목록 조회.
     * API 명세서 §4.1 GET /api/character/v1/character-types
     *
     * - active=true인 캐릭터 타입만 반환한다.
     * - sortOrder 오름차순으로 정렬한다.
     */
    @Transactional(readOnly = true)
    public List<CharacterTypeResponse> getCharacterTypes() {
        return characterTypeRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(ct -> CharacterTypeResponse.builder()
                        .id(ct.getId())
                        .code(ct.getCode())
                        .name(ct.getName())
                        .summary(ct.getSummary())
                        .sampleLine(ct.getSampleLine())
                        .sortOrder(ct.getSortOrder())
                        .build())
                .toList();
    }
}
