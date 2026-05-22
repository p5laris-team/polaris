package p5laris.item.domain.application;

import com.p5laris.proto.item.v1.GetSkinAssetsRequest;
import com.p5laris.proto.item.v1.GetSkinAssetsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.item.domain.domain.entity.Item;
import p5laris.item.domain.domain.repository.ItemRepository;
import p5laris.item.domain.domain.repository.UserItemRepository;
import p5laris.item.domain.domain.repository.UserItemUsageRepository;
import p5laris.item.domain.exception.ItemException;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private UserItemUsageRepository userItemUsageRepository;

    @InjectMocks
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(itemService, "cdnBaseUrl", "https://d24c6my56k1w5v.cloudfront.net");
    }

    @Test
    @DisplayName("getSkinAssets - 스킨 아이템이 아닌 경우 예외 발생")
    void getSkinAssets_notSkinType_throwsException() {
        // given
        Long skinItemId = 1L;
        Long characterTypeId = 2L;

        Item nonSkinItem = Item.builder()
                .id(skinItemId)
                .name("일반 소비템")
                .itemType("CONSUMABLE")
                .build();

        when(itemRepository.findById(skinItemId)).thenReturn(Optional.of(nonSkinItem));

        GetSkinAssetsRequest request = GetSkinAssetsRequest.newBuilder()
                .setSkinItemId(skinItemId)
                .setCharacterTypeId(characterTypeId)
                .build();

        // when & then
        assertThatThrownBy(() -> itemService.getSkinAssets(request))
                .isInstanceOf(ItemException.class)
                .hasMessageContaining("유효하지 않은 아이템 타입입니다.");
    }

    @Test
    @DisplayName("getSkinAssets - 올바른 요청시 에셋 맵 정상 반환 및 low-energy 케이스 lowEnergy 매핑 확인")
    void getSkinAssets_validRequest_returnsAssetUrls() {
        // given
        Long skinItemId = 10L;
        Long characterTypeId = 2L; // 무무

        Item skinItem = Item.builder()
                .id(skinItemId)
                .name("말랑 별빛 스킨")
                .itemType("SKIN")
                .build();

        Item idleAsset = Item.builder()
                .id(11L)
                .name("말랑 별빛 스킨 - 무무 idle")
                .imageUrl("/assets/skins/starlight/equipped/mumu/core/skin-starlight-mumu-idle.png")
                .characterTypeId(characterTypeId)
                .build();

        Item lowEnergyAsset = Item.builder()
                .id(12L)
                .name("말랑 별빛 스킨 - 무무 low-energy")
                .imageUrl("/assets/skins/starlight/equipped/mumu/status/skin-starlight-mumu-low-energy.png")
                .characterTypeId(characterTypeId)
                .build();

        when(itemRepository.findById(skinItemId)).thenReturn(Optional.of(skinItem));
        when(itemRepository.findByNameStartingWithAndCharacterTypeId("말랑 별빛 스킨 - 무무 ", characterTypeId))
                .thenReturn(Arrays.asList(idleAsset, lowEnergyAsset));

        GetSkinAssetsRequest request = GetSkinAssetsRequest.newBuilder()
                .setSkinItemId(skinItemId)
                .setCharacterTypeId(characterTypeId)
                .build();

        // when
        GetSkinAssetsResponse response = itemService.getSkinAssets(request);

        // then
        assertThat(response.getAssetUrlsCount()).isEqualTo(2);
        assertThat(response.getAssetUrlsOrThrow("idle"))
                .isEqualTo("https://d24c6my56k1w5v.cloudfront.net/assets/skins/starlight/equipped/mumu/core/skin-starlight-mumu-idle.png");
        assertThat(response.getAssetUrlsOrThrow("lowEnergy"))
                .isEqualTo("https://d24c6my56k1w5v.cloudfront.net/assets/skins/starlight/equipped/mumu/status/skin-starlight-mumu-low-energy.png");
    }
}
