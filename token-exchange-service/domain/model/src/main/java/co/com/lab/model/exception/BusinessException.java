package co.com.lab.model.exception;

public class BusinessException extends RuntimeException {

    private final ErrorType type;

    public BusinessException(ErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public ErrorType getType() {
        return type;
    }
}
