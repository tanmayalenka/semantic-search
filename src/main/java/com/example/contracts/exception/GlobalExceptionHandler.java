package com.example.contracts.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Client errors from the search service (blank query, too long, etc.)
     * become RFC 7807 ProblemDetail responses with a 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid request");
        return pd;
    }

    /**
     * Embedding-dimension mismatches, Ollama being down, etc. These are
     * server-side misconfigurations, not client errors — 500 is correct,
     * but we surface a readable message instead of a stack trace.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleServerError(IllegalStateException ex) {
        log.error("Server error during request", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Search backend is misconfigured. Check server logs.");
        pd.setTitle("Search unavailable");
        return pd;
    }
}