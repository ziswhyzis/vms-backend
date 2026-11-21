package com.starkgroup.vms.error;

public class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Invalid username or password.");
    }
}
