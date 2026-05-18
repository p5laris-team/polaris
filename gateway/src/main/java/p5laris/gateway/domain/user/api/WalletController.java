package p5laris.gateway.domain.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import p5laris.gateway.domain.user.api.dto.WalletDto;
import p5laris.gateway.domain.user.infrastructure.grpc.WalletGatewayService;
import p5laris.gateway.global.auth.LoginUserId;
import p5laris.gateway.global.common.ApiResponse;

@RestController
@RequestMapping("/api/wallet/v1")
@RequiredArgsConstructor
public class WalletController {

    private final WalletGatewayService walletGatewayService;

    /**
     * 별조각 잔액 조회
     * @param userId
     * @return
     */
    @GetMapping("/wallets/me")
    public ApiResponse<WalletDto.Response> getMyWallet(@LoginUserId Long userId) {
        return ApiResponse.success(walletGatewayService.getMyWallet(userId));
    }
}
