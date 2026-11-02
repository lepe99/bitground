package bit.bitgroundspring.common.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. GlobalExceptionHandler가 ErrorCode의 상태 코드로 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 기본 메시지 대신 상황에 맞는 설명을 내려줄 때
    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
