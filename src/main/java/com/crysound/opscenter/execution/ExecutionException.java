package com.crysound.opscenter.execution;

import java.util.List;

/** 对浏览器输出固定错误码，不包含原参数、上游正文或凭据。 */
public class ExecutionException extends RuntimeException {
    private final int status;
    private final String code;
    public record FieldError(String field, String label, String code, String rule, String message) { }
    private final List<FieldError> fieldErrors;
    public ExecutionException(int status, String code, String message) {
        this(status, code, message, List.of());
    }
    public ExecutionException(int status, String code, String message, List<FieldError> fieldErrors) {
        super(message); this.status = status; this.code = code; this.fieldErrors = List.copyOf(fieldErrors);
    }
    public int status() { return status; }
    public String code() { return code; }
    public List<FieldError> fieldErrors() { return fieldErrors; }
}
