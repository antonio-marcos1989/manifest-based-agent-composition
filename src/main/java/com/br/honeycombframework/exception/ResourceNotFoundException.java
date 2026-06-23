package com.br.honeycombframework.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends HoneycombException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
}
