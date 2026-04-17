package com.warehouse.ontology.api;

import com.warehouse.ontology.support.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateKey(DuplicateKeyException exception) {
        return new ErrorResponse("Object with this ID already exists");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception exception) {
        log.error("Unhandled exception in API request", exception);
        return new ErrorResponse("Unexpected server error");
    }

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpectedError(Throwable error) {
        log.error("Unhandled error in API request: {}", error.getClass().getSimpleName(), error);
        return new ErrorResponse("Server error: " + error.getClass().getSimpleName());
    }

    public record ErrorResponse(String error) {
    }
}
