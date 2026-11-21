package com.starkgroup.vms.error;

public class DuplicateUsernameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String username;

    public DuplicateUsernameException(String username) {
        super("Username '" + username + "' is already taken.");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
