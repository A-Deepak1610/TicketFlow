package com.deepak.ticketflow.controller;
import com.deepak.ticketflow.dto.UserDto;
import com.deepak.ticketflow.model.User;
import com.deepak.ticketflow.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserContoller {
    @Autowired
    private UserService userService;
    @GetMapping("/")
    public String home(){
        return "Server is running";
    }
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserDto user){
        if (userService.exists(user.userName())) {
            return new ResponseEntity<>("User name already exists, try different one", HttpStatus.BAD_REQUEST);
        }
        User rUser=userService.registerUser(user);
        return new ResponseEntity<>("User registered successfully, your username:"+rUser.getUserName(),HttpStatus.OK);
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        Map<String,String> tokens = userService.verify(user);
        if(tokens == null){
            return new ResponseEntity<>("Not authorised", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(tokens, HttpStatus.OK);
    }
}
