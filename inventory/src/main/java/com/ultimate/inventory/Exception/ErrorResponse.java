package com.ultimate.inventory.Exception;

import java.util.List;

public class ErrorResponse {

    private final boolean success = false;
    private final String message;
    private final List<String> errors;

    public ErrorResponse(String message) {
        this.message = message;
        this.errors = null;
    }

    public ErrorResponse(String message, List<String> errors) {
        this.message = message;
        this.errors = errors;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<String> getErrors() { return errors; }
}
