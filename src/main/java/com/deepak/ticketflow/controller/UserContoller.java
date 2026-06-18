package com.deepak.ticketflow.controller;
import com.deepak.ticketflow.dto.UserDto;
import com.deepak.ticketflow.model.User;
import com.deepak.ticketflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "Authentication", description = "APIs for user registration, login, and token refresh")
public class UserContoller {
    @Autowired
    private UserService userService;

    @Operation(
        summary = "Health Check / Home",
        description = "A simple public endpoint to check if the TicketFlow API server is up and running."
    )
    @GetMapping("/")
    public String home(){
        return "Server is running";
    }

    @Operation(
        summary = "Register User",
        description = "Registers a new user into the system with the provided username and password. Checks for username uniqueness."
    )
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserDto user){
        if (userService.exists(user.userName())) {
            return new ResponseEntity<>("User name already exists, try different one", HttpStatus.BAD_REQUEST);
        }
        User rUser=userService.registerUser(user);
        return new ResponseEntity<>("User registered successfully, your username:"+rUser.getUserName(),HttpStatus.OK);
    }

    @Operation(
        summary = "User Login",
        description = "Authenticates user credentials. Returns an access JWT token and a refresh JWT token if successful."
    )
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        Map<String,String> tokens = userService.verify(user);
        if(tokens == null){
            return new ResponseEntity<>("Not authorised", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(tokens, HttpStatus.OK);
    }

    @Operation(
        summary = "Refresh Token",
        description = "Receives a refresh token and generates a new access token for the user, allowing continuous authentication without relogging."
    )
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String,String> request){
        String refreshToken = request.get("refreshToken");
        try{
            String newAccessToken =
                    userService.generateNewAccessToken(refreshToken);
            Map<String,String> response = new HashMap<>();
            response.put("accessToken", newAccessToken);
            return new ResponseEntity<>(response,HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity<>("Invalid refresh token",
                    HttpStatus.UNAUTHORIZED);
        }
    }
}
