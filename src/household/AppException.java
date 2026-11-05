package household;

/** 처리 중 문제가 생겼을 때 화면에 알려줄 오류. */
public final class AppException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 오류 상태. */
    public enum Code {
        /** 입력값 오류. */
        VALIDATION_ERROR,
        /** 대상 없음. */
        NOT_FOUND,
        /** 저장·읽기 오류. */
        IO_ERROR,
        /** 목표 미연결. */
        GOAL_REQUIRED,
        /** 중복 반영. */
        ALREADY_APPLIED,
        /** 미래 분석 월. */
        INVALID_MONTH,
        /** 다른 실행이 파일 사용 중. */
        DATA_IN_USE
    }

    private final Code code;

    /**
     * 어떤 문제인지와 화면에 보여줄 설명을 담는다.
     * @param code 상태 코드
     * @param message 사용자 안내
     */
    public AppException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 오류 상태를 반환한다.
     * @return 코드
     */
    public Code code() {
        return code;
    }
}
