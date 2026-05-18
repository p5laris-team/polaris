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

    /**
     * Get character assets (API spec 4.2).
     * GET /api/character/v1/character-types/{characterTypeId}/assets
     */
    @GetMapping("/v1/character-types/{characterTypeId}/assets")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.CharacterAssetsResponse> getCharacterAssets(
            @org.springframework.web.bind.annotation.PathVariable Long characterTypeId) {
        return ApiResponse.success(characterGatewayService.getCharacterAssets(characterTypeId));
    }

    /**
     * Create a user character (API spec 4.3).
     * POST /api/character/v1/characters
     */
    @org.springframework.web.bind.annotation.PostMapping("/v1/characters")
    public ApiResponse<p5laris.gateway.domain.character.api.dto.UserCharacterResponse> createCharacter(
            @p5laris.gateway.global.security.annotation.LoginUserId Long userId,
            @org.springframework.web.bind.annotation.RequestBody p5laris.gateway.domain.character.api.dto.CreateCharacterRequest request) {
        return ApiResponse.success(characterGatewayService.createCharacter(userId, request));
    }
}

