package com.deepak.ticketflow.service;

import com.deepak.ticketflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MyUserDetailsService implements UserDetailsService {
    @Autowired
    UserRepository userRepo;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
       com.deepak.ticketflow.model.User user=userRepo.findByUserName(username);
        if(user==null){
            System.out.print("Username not found");
            throw new UsernameNotFoundException(username);
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUserName(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}