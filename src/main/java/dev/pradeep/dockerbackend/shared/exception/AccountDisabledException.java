package dev.pradeep.dockerbackend.shared.exception;

public class AccountDisabledException extends SecurityException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
