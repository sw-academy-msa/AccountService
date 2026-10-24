package com.msa.account.global.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * MVC 요청 처리 중 발생한 예외를 유형별 HTTP 응답으로 변환합니다. 보안 필터 단계의 예외 처리는 별도 설정이 필요합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    // 도메인에서 명시적으로 발생시킨 오류의 상태와 메시지를 그대로 사용합니다.
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business exception: code={}", errorCode.code());
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode, traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    // 요청 DTO의 Bean Validation 실패를 필드별 오류 목록으로 변환합니다.
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toViolation)
                .toList();

        return ResponseEntity
                .status(CommonErrorCode.INVALID_REQUEST.status())
                .body(ErrorResponse.validation(traceId(), violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    // 메서드 인자 등의 제약조건 위반을 입력 오류 응답으로 변환합니다.
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<ErrorResponse.FieldViolation> violations = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ErrorResponse.FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return ResponseEntity
                .status(CommonErrorCode.INVALID_REQUEST.status())
                .body(ErrorResponse.validation(traceId(), violations));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    // 숫자나 UUID 등 요청 파라미터를 기대한 타입으로 변환하지 못한 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.debug("Request parameter type mismatch: parameter={}", exception.getName());
        return response(CommonErrorCode.INVALID_PARAMETER_TYPE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    // 잘못된 JSON이나 본문 변환 실패를 처리합니다.
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Unreadable request body", exception);
        return response(CommonErrorCode.INVALID_REQUEST_BODY);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    // 필수 쿼리 파라미터가 없는 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        log.debug("Missing request parameter: parameter={}", exception.getParameterName());
        return response(CommonErrorCode.MISSING_PARAMETER);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    // 필수 요청 헤더가 없는 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        log.debug("Missing request header: header={}", exception.getHeaderName());
        return response(CommonErrorCode.MISSING_HEADER);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    // 필수 요청 쿠키가 없는 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleMissingCookie(MissingRequestCookieException exception) {
        log.debug("Missing request cookie: cookie={}", exception.getCookieName());
        return response(CommonErrorCode.MISSING_COOKIE);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    // 요청한 리소스나 경로를 찾지 못한 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
        log.debug("Resource not found: path={}", exception.getResourcePath());
        return response(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    // 해당 경로에서 지원하지 않는 HTTP 메서드 요청을 처리합니다.
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.debug("HTTP method not supported: method={}", exception.getMethod());
        return response(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    // 요청 Content-Type을 서버가 처리할 수 없는 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        log.debug("Media type not supported: contentType={}", exception.getContentType());
        return response(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    // Accept 헤더가 요구한 응답 형식을 제공할 수 없는 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        log.debug("No acceptable response media type", exception);
        return response(CommonErrorCode.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    // 업로드 크기가 설정된 제한을 초과한 경우를 처리합니다.
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.debug("Upload size exceeded: maxUploadSize={}", exception.getMaxUploadSize());
        return response(CommonErrorCode.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    // 다른 트랜잭션이 먼저 변경한 데이터의 수정 충돌을 처리합니다.
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException exception) {
        log.warn("Optimistic locking conflict", exception);
        return response(CommonErrorCode.OPTIMISTIC_LOCK_CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    // 도메인에서 별도로 변환하지 않은 DB 제약조건 위반을 공통 충돌 응답으로 처리합니다.
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation", exception);
        return response(CommonErrorCode.DATA_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    // 분류되지 않은 오류는 서버 로그에 남기고 내부 예외 내용은 응답에서 숨깁니다.
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return response(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 검증 오류를 필드명과 메시지로 변환합니다. 거절된 원본 값은 응답에 포함하지 않습니다.
    private ErrorResponse.FieldViolation toViolation(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null
                ? CommonErrorCode.INVALID_REQUEST.message()
                : fieldError.getDefaultMessage();
        return new ErrorResponse.FieldViolation(fieldError.getField(), message);
    }

    // 현재 요청의 로그 식별자를 가져와 오류 응답과 서버 로그를 연결합니다.
    private String traceId() {
        return MDC.get("traceId");
    }

    // 오류 정의에 맞는 HTTP 상태와 공통 응답 본문을 조합합니다.
    private ResponseEntity<ErrorResponse> response(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode, traceId()));
    }
}
