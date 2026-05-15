package p5laris.gateway.domain.ai.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.ai.api.dto.AiDto;
import p5laris.gateway.domain.ai.infrastructure.grpc.AiGatewayService;

@RestController
public class AiController {

    private final AiGatewayService aiGatewayService;

    public AiController(AiGatewayService aiGatewayService) {
        this.aiGatewayService = aiGatewayService;
    }

    @GetMapping("/api/ai")
    public AiDto getAi(AiDto aiDto) {
        return new AiDto(aiGatewayService.getAi(aiDto.msg()));
    }
}
