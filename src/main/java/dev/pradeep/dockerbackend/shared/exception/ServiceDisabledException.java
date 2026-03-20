package dev.pradeep.dockerbackend.shared.exception;

public class ServiceDisabledException extends SecurityException {

    public ServiceDisabledException(String message) {
        super(message);
    }
}
