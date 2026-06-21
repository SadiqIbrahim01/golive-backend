package com.golivebackend.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Entity representing an Admin user in the system.
 */
@Entity
@Table(name="admins")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "admin_id", updatable = false, nullable = false)
    private UUID adminId;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;
}
