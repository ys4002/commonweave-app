package com.commonweave.common;

import java.time.Instant;
import java.util.List;

public record ApiError(int status, String code, String message, List<FieldErrorDto> fieldErrors,
                       Instant timestamp, String path) {
}
