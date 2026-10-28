package com.fixtech.exception;

public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String mensaje) {
        super(mensaje);
    }
}
