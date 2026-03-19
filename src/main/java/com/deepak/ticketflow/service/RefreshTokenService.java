package com.deepak.ticketflow.service;

import com.deepak.ticketflow.model.RefreshToken;
import com.deepak.ticketflow.model.User;
import com.deepak.ticketflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    @Autowired
    com.deepak.ticketflow.repository.RefreshTokenRepository refreshTokenRepository;
    @Autowired
    UserRepository userRepo;
    private final long REFRESH_TOKEN_DURATION = 7 * 24 * 60 * 60;
    public RefreshToken generateRefreshToken(String userName) {
        RefreshToken refreshToken = new RefreshToken();
        User user = userRepo.findByUserName(userName);
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusSeconds(REFRESH_TOKEN_DURATION));
        refreshToken.setToken(UUID.randomUUID().toString());
        return refreshTokenRepository.save(refreshToken);
    }
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token expired");
        }
        return token;
    }
    public Optional<RefreshToken> findByToken(String token){
        return refreshTokenRepository.findByToken(token);
    }
}