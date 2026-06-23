package com.br.honeycombframework.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends HoneycombException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
    }
}
