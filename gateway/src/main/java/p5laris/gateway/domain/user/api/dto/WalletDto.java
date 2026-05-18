package p5laris.gateway.domain.user.api.dto;

import lombok.Builder;
import lombok.Getter;

public class WalletDto {

    @Getter
    @Builder
    public static class Response {
        private int starPiece;
    }
}
