package dev.pradeep.dockerbackend.shared.exception;

public class InvalidCredentialsException extends SecurityException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
