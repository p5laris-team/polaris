package p5laris.gateway.domain.item.api.dto;

import java.util.List;

public class ItemDto {

    public record ShopItem(
        Long id,
        String name,
        String description,
        String itemType,
        int price,
        String imageUrl,
        boolean owned
    ) {}

    public record UserItem(
        Long userItemId,
        Long itemId,
        String name,
        String itemType,
        String effectType,
        int quantity
    ) {}

    public record PageInfo(
        String nextCursor,
        boolean hasNext,
        int size
    ) {}

    public record GetItemsResponse(
        List<ShopItem> items,
        PageInfo pageInfo
    ) {}

    public record GetUserItemsResponse(
        List<UserItem> items,
        PageInfo pageInfo
    ) {}

    public record PurchaseRequest(
        Long itemId,
        int quantity
    ) {}

    public record PurchaseResponse(
        Long purchaseId,
        Long itemId,
        String name,
        int quantity,
        int price,
        WalletSummary wallet,
        Long transactionId
    ) {
        public record WalletSummary(
            int starPiece
        ) {}
    }
}
