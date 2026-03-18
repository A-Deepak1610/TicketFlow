package com.deepak.ticketflow.model;

import com.deepak.ticketflow.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    @Column(nullable=false)
    String name;
    @Column(nullable=false)
    String password;
    @Enumerated(EnumType.STRING)
    Role role;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
