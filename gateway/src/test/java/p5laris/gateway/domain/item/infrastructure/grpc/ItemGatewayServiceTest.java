package p5laris.gateway.domain.item.infrastructure.grpc;

import com.p5laris.proto.item.v1.GetItemsRequest;
import com.p5laris.proto.item.v1.GetItemsResponse;
import com.p5laris.proto.item.v1.GetSkinAssetsRequest;
import com.p5laris.proto.item.v1.GetSkinAssetsResponse;
import com.p5laris.proto.item.v1.GetUserItemsRequest;
import com.p5laris.proto.item.v1.GetUserItemsResponse;
import com.p5laris.proto.item.v1.ItemServiceGrpc;
import com.p5laris.proto.item.v1.PurchaseItemRequest;
import com.p5laris.proto.item.v1.PurchaseItemResponse;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import p5laris.gateway.domain.item.exception.ItemGatewayErrorCode;
import p5laris.gateway.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemGatewayServiceTest {

    private ItemServiceGrpc.ItemServiceBlockingStub stub;
    private ItemGatewayService service;

    @BeforeEach
    void setUp() {
        stub = mock(ItemServiceGrpc.ItemServiceBlockingStub.class);
        service = new ItemGatewayService();
        ReflectionTestUtils.setField(service, "itemStub", stub);
    }

    @Test
    void mapsAllItemRequestsAndDefaults() {
        GetItemsRequest getItemsRequest = GetItemsRequest.newBuilder().setSize(20).build();
        GetUserItemsRequest getUserItemsRequest = GetUserItemsRequest.newBuilder()
                .setUserId(7L).setSize(10).build();
        PurchaseItemRequest purchaseRequest = PurchaseItemRequest.newBuilder()
                .setUserId(7L).setItemId(9L).setQuantity(2).build();
        GetSkinAssetsRequest assetsRequest = GetSkinAssetsRequest.newBuilder()
                .setSkinItemId(9L).setCharacterTypeId(3L).build();
        when(stub.getItems(getItemsRequest)).thenReturn(GetItemsResponse.getDefaultInstance());
        when(stub.getUserItems(getUserItemsRequest)).thenReturn(GetUserItemsResponse.getDefaultInstance());
        when(stub.purchaseItem(purchaseRequest)).thenReturn(PurchaseItemResponse.getDefaultInstance());
        when(stub.getSkinAssets(assetsRequest)).thenReturn(GetSkinAssetsResponse.newBuilder()
                .putAssetUrls("front", "front.png").build());

        assertThat(service.getItems(null, null, null, 20)).isNotNull();
        assertThat(service.getUserItems(7L, null, null, 10)).isNotNull();
        assertThat(service.purchaseItem(7L, 9L, 2, null)).isNotNull();
        assertThat(service.getSkinAssets(9L, 3L)).containsEntry("front", "front.png");

        verify(stub).getItems(getItemsRequest);
        verify(stub).getUserItems(getUserItemsRequest);
        verify(stub).purchaseItem(purchaseRequest);
        verify(stub).getSkinAssets(assetsRequest);
    }

    @Test
    void mapsGrpcFailuresToItemErrors() {
        when(stub.getItems(GetItemsRequest.newBuilder().setSize(1).build()))
                .thenThrow(Status.NOT_FOUND.withDescription("USER_ITEM_NOT_FOUND").asRuntimeException());
        assertThatThrownBy(() -> service.getItems(null, null, null, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ItemGatewayErrorCode.USER_ITEM_NOT_FOUND);

        when(stub.getItems(GetItemsRequest.newBuilder().setSize(2).build()))
                .thenThrow(Status.FAILED_PRECONDITION
                        .withDescription("STAR_PIECE_NOT_ENOUGH").asRuntimeException());
        assertThatThrownBy(() -> service.getItems(null, null, null, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ItemGatewayErrorCode.STAR_PIECE_NOT_ENOUGH);

        when(stub.getItems(GetItemsRequest.newBuilder().setSize(3).build()))
                .thenThrow(Status.ALREADY_EXISTS.asRuntimeException());
        assertThatThrownBy(() -> service.getItems(null, null, null, 3))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ItemGatewayErrorCode.ITEM_ALREADY_OWNED);
    }
}
