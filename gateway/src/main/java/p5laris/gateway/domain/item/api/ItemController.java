package p5laris.gateway.domain.item.api;

import com.p5laris.proto.item.v1.GetUserItemsResponse;
import com.p5laris.proto.item.v1.PurchaseItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import p5laris.gateway.domain.item.api.dto.ItemDto;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemGatewayService itemGatewayService;

    /**
     * 상점 아이템 목록 조회
     * @param userId
     * @param itemType
     * @param cursor
     * @param size
     * @return
     */
    @GetMapping("/v1/items")
    public ApiResponse<ItemDto.GetItemsResponse> getItems(
            @LoginUserId Long userId,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        com.p5laris.proto.item.v1.GetItemsResponse protoResponse =
                itemGatewayService.getItems(userId, itemType, cursor, size);

        ItemDto.GetItemsResponse response = new ItemDto.GetItemsResponse(
                protoResponse.getItemsList().stream()
                        .map(item -> new ItemDto.ShopItem(
                                item.getId(),
                                item.getName(),
                                item.getDescription(),
                                item.getItemType(),
                                item.getPrice(),
                                item.getImageUrl(),
                                item.getOwned()
                        ))
                        .collect(Collectors.toList()),
                new ItemDto.PageInfo(
                        protoResponse.getPageInfo().getNextCursor(),
                        protoResponse.getPageInfo().getHasNext(),
                        protoResponse.getPageInfo().getSize()
                )
        );

        return ApiResponse.success(response);
    }

    /**
     * 내 보유 아이템 조회
     * @param userId
     * @param itemType
     * @param cursor
     * @param size
     * @return
     */
    @GetMapping("/v1/user-items")
    public ApiResponse<ItemDto.GetUserItemsResponse> getUserItems(
            @LoginUserId Long userId,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        GetUserItemsResponse protoResponse =
                itemGatewayService.getUserItems(userId, itemType, cursor, size);

        ItemDto.GetUserItemsResponse response = new ItemDto.GetUserItemsResponse(
                protoResponse.getItemsList().stream()
                        .map(ui -> new ItemDto.UserItem(
                                ui.getUserItemId(),
                                ui.getItemId(),
                                ui.getName(),
                                ui.getItemType(),
                                ui.getEffectType(),
                                ui.getQuantity(),
                                ui.getEquipped()
                        ))
                        .collect(Collectors.toList()),
                new ItemDto.PageInfo(
                        protoResponse.getPageInfo().getNextCursor(),
                        protoResponse.getPageInfo().getHasNext(),
                        protoResponse.getPageInfo().getSize()
                )
        );

        return ApiResponse.success(response);
    }

    /**
     * 아이템 구매
     * @param userId
     * @param request
     * @return
     */
    @PostMapping("/v1/item-purchases")
    public ApiResponse<ItemDto.PurchaseResponse> purchaseItem(
            @LoginUserId Long userId,
            @RequestBody ItemDto.PurchaseRequest request
    ) {
        // 중복 방지를 위한 구매 멱등키 생성
        String idempotencyKey = "purchase-" + userId + "-" + request.itemId() + "-" + System.currentTimeMillis();

        PurchaseItemResponse protoResponse =
                itemGatewayService.purchaseItem(userId, request.itemId(), request.quantity(), idempotencyKey);

        ItemDto.PurchaseResponse response = new ItemDto.PurchaseResponse(
                protoResponse.getPurchaseId(),
                protoResponse.getItemId(),
                protoResponse.getName(),
                protoResponse.getQuantity(),
                protoResponse.getPrice(),
                new ItemDto.PurchaseResponse.WalletSummary(
                        protoResponse.getStarPiece()
                ),
                protoResponse.getTransactionId()
        );

        return ApiResponse.success(response);
    }
}
