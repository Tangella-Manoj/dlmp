package com.dlmp.loan.exception;

import com.dlmp.common.dto.ApiResponse;
import com.dlmp.common.dto.ErrorResponse;
import com.dlmp.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock: {}", ex.getMessage());
        return ResponseEntity.status(409)
                .body(ApiResponse.error(409, "Concurrent modification — please retry", "CONCURRENT_MODIFICATION"));
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, "Invalid or out-of-range request data", "INVALID_DATA"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        // Spring rejects an oversized multipart body during request parsing, before
        // the controller (and its own 10MB check) ever runs — without this handler
        // it falls through to the generic 500 below instead of a clean client error.
        log.warn("Upload too large at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(413)
                .body(ApiResponse.error(413, "File too large — max 10MB", "FILE_TOO_LARGE"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartError(MultipartException ex, HttpServletRequest req) {
        log.warn("Multipart parse error at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(400)
                .body(ApiResponse.error(400, "Could not read the uploaded file", "INVALID_UPLOAD"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(403)
                .body(ApiResponse.error(403, "You do not have permission to perform this action", "ACCESS_DENIED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled: {} {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(500, "Internal server error", "INTERNAL_ERROR"));
    }
}
