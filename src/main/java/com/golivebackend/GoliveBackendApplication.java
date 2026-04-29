package com.golivebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
public class GoliveBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoliveBackendApplication.class, args);
    }

}
