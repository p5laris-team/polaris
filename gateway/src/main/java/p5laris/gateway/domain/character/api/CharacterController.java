package p5laris.gateway.domain.character.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.character.api.dto.CharacterDto;
import p5laris.gateway.domain.character.api.dto.CharacterTypesResponse;
import p5laris.gateway.domain.character.infrastructure.grpc.CharacterGatewayService;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterGatewayService characterGatewayService;

    // 기존 스캐폴딩 (핑퐁)
    @GetMapping
    public CharacterDto getCharacter(@RequestParam String msg) {
        return new CharacterDto(characterGatewayService.getCharacter(msg));
    }

    /**
     * Get character types (API spec 4.1).
     * GET /api/character/v1/character-types
     */
    @GetMapping("/v1/character-types")
    public ApiResponse<CharacterTypesResponse> getCharacterTypes() {
        return ApiResponse.success(characterGatewayService.getCharacterTypes());
    }
}

