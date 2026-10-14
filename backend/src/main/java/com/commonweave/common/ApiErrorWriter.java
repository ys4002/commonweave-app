package com.commonweave.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes the contract error body for failures raised inside the security filter chain. */
@Component
public class ApiErrorWriter {
    private final ObjectMapper mapper;

    public ApiErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static ApiError body(ErrorCode code, String message, List<FieldErrorDto> fields, String path) {
        return new ApiError(code.status().value(), code.name(), message, fields, Instant.now(), path);
    }

    public void write(HttpServletRequest req, HttpServletResponse res, ErrorCode code, String message)
            throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), body(code, message, List.of(), req.getRequestURI()));
    }
}
