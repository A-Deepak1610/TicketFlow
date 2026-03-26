package com.deepak.ticketflow.service;

import com.deepak.ticketflow.Enum.Role;
import com.deepak.ticketflow.dto.UserDto;
import com.deepak.ticketflow.model.RefreshToken;
import com.deepak.ticketflow.model.User;
import com.deepak.ticketflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepo;
    @Autowired
    AuthenticationManager authenticationManager;
    @Autowired
    JwtService jwt;
    @Autowired
    RefreshTokenService refreshTokenService;
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    public User registerUser(UserDto userDto) {
        User user = new User();
        user.setUserName(userDto.userName());
        user.setPassword(passwordEncoder.encode(userDto.password()));
        user.setRole(Role.ROLE_ADMIN);
        return userRepo.save(user);
    }
    public boolean exists(String userName){
        return userRepo.existsByUserName(userName);
    }
    public Map<String,String> verify(User user){
        try {
            //in this we are telling the auth manager usernamePassword auth and this is the username and password so this object contains this tokenThis token is sent to AuthenticationManager.
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUserName(), user.getPassword())
            );
            if (authentication.isAuthenticated()) {
                User user1=userRepo.findByUserName(user.getUserName());
                String accessToken=jwt.generateToken(user1);
                RefreshToken refreshToken=refreshTokenService.generateRefreshToken(user.getUserName());
                Map<String,String> tokens = new HashMap<>();
                tokens.put("accessToken", accessToken);
                tokens.put("refreshToken", refreshToken.getToken());
                return  tokens;
            }
        } catch (AuthenticationException e) {
            return null;
        }
        return null;
    }
    public String generateNewAccessToken(String refreshToken) {
        RefreshToken token = refreshTokenService
                .findByToken(refreshToken)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));
        refreshTokenService.verifyExpiration(token);
        String username = token.getUser().getUserName();
        User user = userRepo.findByUserName(username);
        return jwt.generateToken(user);
    }
}
