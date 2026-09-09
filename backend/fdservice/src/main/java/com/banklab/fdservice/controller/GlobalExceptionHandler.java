package com.banklab.fdservice.controller;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.banklab.fdservice.client.UpstreamServiceException;
import com.banklab.fdservice.dto.ErrorResponse;
import com.banklab.fdservice.service.AccountSequenceNotConfiguredException;
import com.banklab.fdservice.service.CategoryMappingUnresolvedException;
import com.banklab.fdservice.service.RoleCardinalityViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamService(UpstreamServiceException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UPSTREAM_ERROR -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(ex.getReason().name(), ex.getMessage()));
    }

    @ExceptionHandler(CategoryMappingUnresolvedException.class)
    public ResponseEntity<ErrorResponse> handleCategoryUnresolved(CategoryMappingUnresolvedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse("CATEGORY_UNRESOLVED", ex.getMessage()));
    }

    @ExceptionHandler(RoleCardinalityViolationException.class)
    public ResponseEntity<ErrorResponse> handleRoleCardinality(RoleCardinalityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("ROLE_CARDINALITY_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(AccountSequenceNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleSequenceNotConfigured(AccountSequenceNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("ACCOUNT_SEQUENCE_NOT_CONFIGURED", ex.getMessage()));
    }
}
