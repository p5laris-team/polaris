package p5laris.item.domain.application;

import com.p5laris.proto.item.v1.*;
import com.p5laris.proto.user.v1.SpendStarPieceRequest;
import com.p5laris.proto.user.v1.SpendStarPieceResponse;
import com.p5laris.proto.user.v1.WalletServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import p5laris.item.domain.application.event.ItemEventLogEvent;
import p5laris.item.domain.domain.entity.Item;
import p5laris.item.domain.domain.entity.UserItem;
import p5laris.item.domain.domain.entity.UserItemUsage;
import p5laris.item.domain.domain.repository.ItemRepository;
import p5laris.item.domain.domain.repository.UserItemRepository;
import p5laris.item.domain.domain.repository.UserItemUsageRepository;
import p5laris.item.domain.exception.ItemErrorCode;
import p5laris.item.domain.exception.ItemException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final UserItemUsageRepository userItemUsageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @GrpcClient("user")
    private WalletServiceGrpc.WalletServiceBlockingStub walletStub;

    @Transactional(readOnly = true)
    public GetItemsResponse getItems(GetItemsRequest request) {
        Long cursorId = parseCursor(request.getCursor());
        int size = request.getSize() > 0 ? request.getSize() : 20;
        Pageable pageable = PageRequest.of(0, size + 1);
        
        List<Item> items;
        String itemType = request.getItemType();
        if (itemType != null && !itemType.isEmpty() && !itemType.equals("ALL")) {
            items = itemRepository.findByIdGreaterThanAndItemTypeAndActiveOrderByIdAsc(cursorId, itemType, true, pageable);
        } else {
            items = itemRepository.findByIdGreaterThanAndActiveOrderByIdAsc(cursorId, true, pageable);
        }
        
        boolean hasNext = items.size() > size;
        List<Item> content = hasNext ? items.subList(0, size) : items;
        
        Set<Long> ownedItemIds = new HashSet<>();
        if (request.getUserId() > 0) {
            ownedItemIds = userItemRepository.findByUserId(request.getUserId()).stream()
                    .map(ui -> ui.getItem().getId())
                    .collect(Collectors.toSet());
        }
        
        GetItemsResponse.Builder responseBuilder = GetItemsResponse.newBuilder();
        for (Item item : content) {
            responseBuilder.addItems(
                com.p5laris.proto.item.v1.Item.newBuilder()
                    .setId(item.getId())
                    .setName(item.getName())
                    .setDescription(item.getDescription() != null ? item.getDescription() : "")
                    .setItemType(item.getItemType())
                    .setPrice(item.getPrice())
                    .setImageUrl(item.getImageUrl() != null ? item.getImageUrl() : "")
                    .setOwned(ownedItemIds.contains(item.getId()))
                    .setEffectType(item.getEffectType() != null ? item.getEffectType() : "")
                    .setCharacterTypeId(item.getCharacterTypeId() != null ? item.getCharacterTypeId() : 0L)
                    .build()
            );
        }
        
        Long nextCursorId = content.isEmpty() ? null : content.get(content.size() - 1).getId();
        responseBuilder.setPageInfo(
            PageInfo.newBuilder()
                .setNextCursor(nextCursorId != null ? encodeCursor(nextCursorId) : "")
                .setHasNext(hasNext)
                .setSize(size)
                .build()
        );
        
        return responseBuilder.build();
    }

    @Transactional(readOnly = true)
    public GetUserItemsResponse getUserItems(GetUserItemsRequest request) {
        Long cursorId = parseCursor(request.getCursor());
        int size = request.getSize() > 0 ? request.getSize() : 20;
        Pageable pageable = PageRequest.of(0, size + 1);
        
        List<UserItem> userItems;
        String itemType = request.getItemType();
        if (itemType != null && !itemType.isEmpty() && !itemType.equals("ALL")) {
            userItems = userItemRepository.findByUserIdAndItemItemTypeAndIdGreaterThanOrderByIdAsc(
                    request.getUserId(), itemType, cursorId, pageable);
        } else {
            userItems = userItemRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(
                    request.getUserId(), cursorId, pageable);
        }
        
        boolean hasNext = userItems.size() > size;
        List<UserItem> content = hasNext ? userItems.subList(0, size) : userItems;
        
        GetUserItemsResponse.Builder responseBuilder = GetUserItemsResponse.newBuilder();
        for (UserItem ui : content) {
            responseBuilder.addItems(
                com.p5laris.proto.item.v1.UserItem.newBuilder()
                    .setUserItemId(ui.getId())
                    .setItemId(ui.getItem().getId())
                    .setName(ui.getItem().getName())
                    .setItemType(ui.getItem().getItemType())
                    .setEffectType(ui.getItem().getEffectType() != null ? ui.getItem().getEffectType() : "")
                    .setQuantity(ui.getQuantity())
                    .setCharacterTypeId(ui.getItem().getCharacterTypeId() != null ? ui.getItem().getCharacterTypeId() : 0L)
                    .setImageUrl(ui.getItem().getImageUrl() != null ? ui.getItem().getImageUrl() : "")
                    .build()
            );
        }
        
        Long nextCursorId = content.isEmpty() ? null : content.get(content.size() - 1).getId();
        responseBuilder.setPageInfo(
            PageInfo.newBuilder()
                .setNextCursor(nextCursorId != null ? encodeCursor(nextCursorId) : "")
                .setHasNext(hasNext)
                .setSize(size)
                .build()
        );
        
        return responseBuilder.build();
    }

    @Transactional
    public PurchaseItemResponse purchaseItem(PurchaseItemRequest request) {
        Long userId = request.getUserId();
        Long itemId = request.getItemId();
        int quantity = request.getQuantity() > 0 ? request.getQuantity() : 1;
        
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ItemException(ItemErrorCode.ITEM_NOT_FOUND));
        
        // 스킨 중복 구매 검증
        if ("SKIN".equals(item.getItemType())) {
            Optional<UserItem> existingSkin = userItemRepository.findByUserIdAndItemId(userId, itemId);
            if (existingSkin.isPresent()) {
                throw new ItemException(ItemErrorCode.ITEM_ALREADY_OWNED);
            }
            quantity = 1;
        }
        
        int totalPrice = item.getPrice() * quantity;
        
        // 지갑 별조각 차감
        SpendStarPieceResponse spendResponse;
        try {
            spendResponse = walletStub.spendStarPiece(
                SpendStarPieceRequest.newBuilder()
                    .setUserId(userId)
                    .setAmount(totalPrice)
                    .setReason("ITEM_PURCHASE")
                    .setRefType("ITEM")
                    .setRefId(itemId)
                    .setIdempotencyKey(request.getIdempotencyKey())
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to spend star piece for userId: {}, amount: {}, itemId: {}", userId, totalPrice, itemId, e);
            String errMsg = e.getMessage();
            if (errMsg != null && (errMsg.contains("STAR_PIECE_NOT_ENOUGH") || errMsg.contains("별조각이 부족합니다."))) {
                throw new ItemException(ItemErrorCode.STAR_PIECE_NOT_ENOUGH);
            }
            throw new ItemException(ItemErrorCode.WALLET_SERVICE_CALL_FAILED);
        }
        
        // UserItem 적재
        UserItem userItem = userItemRepository.findByUserIdAndItemId(userId, itemId)
                .orElse(null);
                
        if (userItem != null) {
            userItem.addQuantity(quantity);
        } else {
            userItem = UserItem.builder()
                    .userId(userId)
                    .item(item)
                    .quantity(quantity)
                    .build();
        }
        UserItem savedUserItem = userItemRepository.save(userItem);

        eventPublisher.publishEvent(ItemEventLogEvent.itemPurchased(
                userId,
                savedUserItem,
                item,
                quantity,
                totalPrice,
                spendResponse.getTransactionId(),
                spendResponse.getStarPiece()
        ));
        eventPublisher.publishEvent(ItemEventLogEvent.starPieceSpent(
                userId,
                item,
                totalPrice,
                spendResponse.getTransactionId(),
                spendResponse.getStarPiece(),
                request.getIdempotencyKey()
        ));
        
        return PurchaseItemResponse.newBuilder()
                .setPurchaseId(savedUserItem.getId())
                .setItemId(itemId)
                .setName(item.getName())
                .setQuantity(quantity)
                .setPrice(totalPrice)
                .setStarPiece(spendResponse.getStarPiece())
                .setTransactionId(spendResponse.getTransactionId())
                .build();
    }

    /**
     * 소모성(CONSUMABLE) 아이템을 1개 사용한다.
     *
     * - character 모듈이 돌봄 액션 시 호출한다.
     * - UserItem.quantity 를 차감하고 item_usage_histories 에 이력을 기록한다.
     * - idempotencyKey 로 중복 처리를 방지한다.
     */
    @Transactional
    public UseItemResponse useItem(UseItemRequest request) {
        Long userId  = request.getUserId();
        Long itemId  = request.getItemId();
        int  qty     = request.getQuantity() > 0 ? request.getQuantity() : 1;
        String idempotencyKey = request.getIdempotencyKey().isEmpty() ? null : request.getIdempotencyKey();

        // 멱등성 검사: 이미 처리된 요청이면 기존 결과를 그대로 반환한다.
        if (idempotencyKey != null) {
            Optional<UserItemUsage> existing = userItemUsageRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                UserItemUsage dup = existing.get();
                UserItem dupUserItem = dup.getUserItem();
                return UseItemResponse.newBuilder()
                        .setUsageId(dup.getId())
                        .setUserItemId(dupUserItem.getId())
                        .setItemId(dup.getItemId())
                        .setQuantityUsed(dup.getQuantity())
                        .setRemainingQuantity(dupUserItem.getQuantity())
                        .build();
            }
        }

        // 보유 아이템 조회
        UserItem userItem = userItemRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new ItemException(ItemErrorCode.USER_ITEM_NOT_FOUND));

        // 수량 검증
        if (userItem.getQuantity() < qty) {
            throw new ItemException(ItemErrorCode.ITEM_QUANTITY_NOT_ENOUGH);
        }

        // 수량 차감
        userItem.useQuantity(qty);

        // 사용 이력 기록
        UserItemUsage usage = UserItemUsage.builder()
                .userId(userId)
                .userItem(userItem)
                .itemId(itemId)
                .quantity(qty)
                .refType(request.getRefType().isEmpty() ? null : request.getRefType())
                .refId(request.getRefId() == 0 ? null : request.getRefId())
                .idempotencyKey(idempotencyKey)
                .build();
        userItemUsageRepository.save(usage);

        return UseItemResponse.newBuilder()
                .setUsageId(usage.getId())
                .setUserItemId(userItem.getId())
                .setItemId(itemId)
                .setQuantityUsed(qty)
                .setRemainingQuantity(userItem.getQuantity())
                .build();
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0L;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cursor);
            String rawId = new String(decoded);
            return Long.parseLong(rawId);
        } catch (Exception e) {
            try {
                return Long.parseLong(cursor);
            } catch (NumberFormatException nfe) {
                return 0L;
            }
        }
    }

    private String encodeCursor(Long id) {
        if (id == null) return "";
        return Base64.getEncoder().encodeToString(String.valueOf(id).getBytes());
    }
}
