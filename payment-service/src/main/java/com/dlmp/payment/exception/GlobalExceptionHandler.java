package com.dlmp.payment.exception;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.dto.ErrorResponse;
import com.dlmp.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handle(ServiceException ex, HttpServletRequest req) {
        log.warn("[{}] {}: {}", ex.getErrorCode(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getStatus().value(), ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField()).message(fe.getDefaultMessage()).rejectedValue(fe.getRejectedValue()).build())
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .success(false).statusCode(400).message("Validation failed")
                .errorCode("VALIDATION_ERROR").path(req.getRequestURI()).fieldErrors(errors).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(500, "Internal error", "INTERNAL_ERROR"));
    }
}
