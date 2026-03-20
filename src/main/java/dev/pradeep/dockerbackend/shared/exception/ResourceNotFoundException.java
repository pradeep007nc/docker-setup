package dev.pradeep.dockerbackend.shared.exception;

public class ResourceNotFoundException extends SecurityException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
