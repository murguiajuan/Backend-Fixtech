package com.fixtech.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String mensaje) {
        super(mensaje);
    }
}
