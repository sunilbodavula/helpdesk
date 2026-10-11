package com.example.helpdesk.exception;

import com.example.helpdesk.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;


@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex, WebRequest request){
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request){
        log.warn("Data integrity violation: {}",
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request){
        log.error("Unexpected Error" + ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error", request, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ){
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", request, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ){
        return build(HttpStatus.BAD_REQUEST,
                "Request body is missing, malformed, or contains an invalid value", request, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ){
        String message = "Request could not be processed";
        if(body instanceof ProblemDetail pd && pd.getDetail() != null){
            message = pd.getDetail();
        }else if(ex instanceof ErrorResponse er && er.getBody().getDetail() != null){
            message = er.getBody().getDetail();
        }
        return build(statusCode, message, request, null, null);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint found for " + pathOf(request), request, headers, null);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + name + "'", request, headers, null);
    }

    private ResponseEntity<Object> build(
            HttpStatusCode status,
            String message,
            WebRequest request,
            HttpHeaders headers,
            List<ApiError.FieldViolation> errors
    ){
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        ApiError body = new ApiError(Instant.now(), status.value(), reason, message, pathOf(request), errors);
        return new ResponseEntity<>(body, headers, status);
    }

    private String pathOf(WebRequest request){
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI(): null;
    }
}
