package com.datatalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.datatalk")
public class DataTalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataTalkApplication.class, args);
    }
}
