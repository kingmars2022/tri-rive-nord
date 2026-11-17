package com.tririvenord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TriRiveNordApplication {
    public static void main(String[] args) {
        SpringApplication.run(TriRiveNordApplication.class, args);
    }
}

