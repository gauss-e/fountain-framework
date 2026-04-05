package com.fountainframework.common;

public class GaussException extends RuntimeException {

    public GaussException(String message) {
        super(message);
    }

    public GaussException(String message, Throwable cause) {
        super(message, cause);
    }
}
