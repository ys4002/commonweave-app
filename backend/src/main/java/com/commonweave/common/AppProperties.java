package com.commonweave.common;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String timezone, Cors cors) {
    public record Cors(List<String> allowedOrigins) {
    }
}
