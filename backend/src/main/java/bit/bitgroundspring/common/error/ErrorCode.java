package bit.bitgroundspring.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드.
 * 에러 응답(RFC 9457 Problem Details)의 code 필드로 내려가며,
 * 프론트는 HTTP 상태 대신 이 값으로 분기한다.
 *
 * 상태 코드 기준
 * - 400 요청 형식·값이 잘못됨
 * - 401 인증 필요 / 403 권한 없음
 * - 404 대상 없음
 * - 409 현재 상태와 충돌 (이미 처리됨, 진행 중인 시즌 없음 등)
 * - 422 형식은 맞지만 비즈니스 규칙상 처리 불가 (잔액 부족 등)
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 호출에 실패했습니다."),

    // 인증·권한
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 없습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다."),
    INVALID_SECRET_KEY(HttpStatus.FORBIDDEN, "유효하지 않은 키입니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 커뮤니티·공지
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),
    NOT_AUTHOR(HttpStatus.FORBIDDEN, "작성자만 처리할 수 있습니다."),

    // 시장·시즌
    COIN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 코인입니다."),
    COIN_DELISTED(HttpStatus.CONFLICT, "거래가 중단된 코인입니다."),
    SEASON_NOT_FOUND(HttpStatus.NOT_FOUND, "시즌을 찾을 수 없습니다."),
    NO_ACTIVE_SEASON(HttpStatus.CONFLICT, "진행 중인 시즌이 없습니다."),
    SEASON_NOT_COMPLETED(HttpStatus.CONFLICT, "종료된 시즌에서만 요청할 수 있습니다."),
    AI_INSIGHT_NOT_FOUND(HttpStatus.NOT_FOUND, "오늘의 AI 인사이트가 없습니다."),
    AI_ADVICE_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "AI 조언을 생성할 수 없습니다."),

    // 즐겨찾기
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 즐겨찾기에 등록된 코인입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "즐겨찾기에 없는 코인입니다."),

    // 주문
    INVALID_ORDER(HttpStatus.BAD_REQUEST, "주문 정보가 올바르지 않습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_NOT_PENDING(HttpStatus.CONFLICT, "이미 체결되었거나 취소된 주문입니다."),
    INSUFFICIENT_CASH(HttpStatus.UNPROCESSABLE_ENTITY, "주문 가능 금액이 부족합니다."),
    INSUFFICIENT_ASSET(HttpStatus.UNPROCESSABLE_ENTITY, "주문 가능 수량이 부족합니다."),
    PRICE_OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "지정가가 시장가에서 너무 벗어났습니다."),

    // 파일 업로드
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "파일이 비어 있습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 너무 큽니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "jpg, png, gif, webp 이미지만 업로드할 수 있습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    // Spring이 처리한 표준 예외(검증 실패, 404 등)에 붙일 기본 코드
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> INVALID_INPUT;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> FILE_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? INTERNAL_ERROR : INVALID_INPUT;
        };
    }
}
