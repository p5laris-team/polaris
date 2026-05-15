package p5laris.gateway.domain.character.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.character.api.dto.CharacterDto;
import p5laris.gateway.domain.character.infrastructure.grpc.CharacterGatewayService;

@RestController
public class CharacterController {

    private final CharacterGatewayService characterGatewayService;

    public CharacterController(CharacterGatewayService characterGatewayService) {
        this.characterGatewayService = characterGatewayService;
    }

    @GetMapping("/api/character")
    public CharacterDto getCharacter(@RequestParam String msg) {
        return new CharacterDto(characterGatewayService.getCharacter(msg));
    }
}
