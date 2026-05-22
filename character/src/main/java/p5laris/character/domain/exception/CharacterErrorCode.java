package p5laris.character.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CharacterErrorCode {
    CHARACTER_NOT_FOUND("CH001", "캐릭터를 찾을 수 없습니다."),
    CHARACTER_TYPE_NOT_FOUND("CH002", "캐릭터 타입을 찾을 수 없습니다."),
    NOT_CHARACTER_OWNER("CH003", "해당 캐릭터의 소유자가 아닙니다."),
    INVALID_CHARACTER_NAME("CH004", "유효하지 않은 캐릭터 이름입니다."),
    INVALID_ACTION_TYPE("CH005", "유효하지 않은 돌봄 액션입니다."),
    SHARE_CARD_NOT_FOUND("CH006", "공유 카드를 찾을 수 없습니다."),
    NOT_SHARE_CARD_OWNER("CH007", "해당 공유 카드의 소유자가 아닙니다."),
    SHARE_LINK_NOT_FOUND("CH008", "공유 링크를 찾을 수 없습니다."),
    INVALID_SHARE_HEADLINE("CH009", "공유 카드 문구가 유효하지 않습니다."),
    SHARE_CARD_IMAGE_UPLOAD_FAILED("CH010", "공유 카드 이미지 업로드에 실패했습니다."),
    ITEM_NOT_OWNED("CH011", "보유하지 않은 아이템입니다."),
    ITEM_SERVICE_CALL_FAILED("CH012", "아이템 서비스 호출에 실패했습니다.");

    private final String code;
    private final String message;
}
