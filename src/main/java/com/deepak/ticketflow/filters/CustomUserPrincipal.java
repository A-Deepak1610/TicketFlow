package com.deepak.ticketflow.filters;


public class CustomUserPrincipal {
    private Integer userId;
    private String username;
    public CustomUserPrincipal(Integer userId, String username) {
        this.userId = userId;
        this.username = username;
    }
    public Integer getUserId() { return userId; }
    public String getUsername() { return username; }
}
