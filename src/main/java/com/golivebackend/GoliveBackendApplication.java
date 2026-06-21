package com.golivebackend;

import com.golivebackend.admin.model.Admin;
import com.golivebackend.admin.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * @EnableConfigurationProperties activates scanning for classes
 * annotated with @ConfigurationProperties.
 * Without this, LiveKitProperties is not bound to application.yml values.
 *
 * Alternative: add @ConfigurationPropertiesScan to scan the full
 * package automatically — we use the explicit annotation so it's
 * visible and intentional.
 */

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class GoliveBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoliveBackendApplication.class, args);
    }

    /**
     * Seed the database with a default Admin account on startup if none exists.
     * Uses outcoded values defined in application.yml properties as the source,
     * hashes the password securely using BCrypt, and persists it.
     */
    @Bean
    public CommandLineRunner initAdmin(
            AdminRepository adminRepository,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${admin.username}") String adminUsername,
            @Value("${admin.password}") String adminPassword
    ) {
        return args -> {
            if (!adminRepository.existsByUsername(adminUsername)) {
                Admin defaultAdmin = Admin.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .build();
                adminRepository.save(defaultAdmin);
                System.out.println("====== Admin Database Initialisation ======");
                System.out.println("Created default admin user: " + adminUsername);
                System.out.println("===========================================");
            }
        };
    }
}
