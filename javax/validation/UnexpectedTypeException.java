package javax.validation;

public class UnexpectedTypeException extends ConstraintDeclarationException {
    public UnexpectedTypeException(String message) {
        super(message);
    }

    public UnexpectedTypeException() {
    }

    public UnexpectedTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnexpectedTypeException(Throwable cause) {
        super(cause);
    }
}
