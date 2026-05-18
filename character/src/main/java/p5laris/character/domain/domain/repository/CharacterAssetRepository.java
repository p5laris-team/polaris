package p5laris.character.domain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import p5laris.character.domain.domain.entity.CharacterAsset;
import p5laris.character.domain.domain.entity.CharacterType;

import java.util.List;

public interface CharacterAssetRepository extends JpaRepository<CharacterAsset, Long> {

    /** 캐릭터 타입에 속한 모든 에셋 조회 */
    List<CharacterAsset> findByCharacterType(CharacterType characterType);

    /** 캐릭터 타입 ID와 에셋 타입으로 조회 */
    List<CharacterAsset> findByCharacterTypeIdAndAssetType(Long characterTypeId, String assetType);
}
