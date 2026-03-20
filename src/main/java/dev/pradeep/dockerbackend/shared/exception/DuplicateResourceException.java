package dev.pradeep.dockerbackend.shared.exception;

public class DuplicateResourceException extends SecurityException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
