package com.deepak.ticketflow.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    private String token;
    private Instant expiryDate;
    @ManyToOne//One user → multiple devices
    @JoinColumn(name="user_id")
    private User user;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiryDate() {
        return expiryDate;
    }
    public void setExpiryDate(Instant expiryDate) {
        this.expiryDate = expiryDate;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
}