package dev.pradeep.dockerbackend.shared.exception;

public class InvalidOtpException extends SecurityException {

    public InvalidOtpException(String message) {
        super(message);
    }
}
