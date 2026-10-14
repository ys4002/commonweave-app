package com.commonweave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommonweaveApplication {
    public static void main(String[] args) {
        SpringApplication.run(CommonweaveApplication.class, args);
    }
}
