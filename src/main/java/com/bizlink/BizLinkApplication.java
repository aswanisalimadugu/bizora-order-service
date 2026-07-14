package com.bizlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BizLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BizLinkApplication.class, args);
    }
}
