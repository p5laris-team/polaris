package p5laris.gateway.domain.item.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.item.api.dto.ItemDto;
import p5laris.gateway.domain.item.infrastructure.grpc.ItemGatewayService;

@RestController
public class ItemController {

    private final ItemGatewayService itemGatewayService;

    public ItemController(ItemGatewayService itemGatewayService) {
        this.itemGatewayService = itemGatewayService;
    }

    @GetMapping("/api/item")
    public ItemDto getItem(@RequestParam String msg) {
        return new ItemDto(itemGatewayService.getItem(msg));
    }
}
