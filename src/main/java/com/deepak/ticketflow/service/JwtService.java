package com.deepak.ticketflow.service;

import com.deepak.ticketflow.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private String secretKey="fuasvbdfuwbsvfuynrwdgin3ietn34345t345345bgdewuyfgbskadjvbuiowe";

    private Key getKey(){
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }
    public String generateToken(User user){
        System.out.print(user);
        return Jwts.builder()
                .subject(user.getUserName())
                .claim("role",user.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+1000*60*60*30))
                .signWith(getKey())
                .compact();
    }
    public String extractUsername(String token){
        return Jwts.parser()
                .verifyWith((SecretKey) getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();//this retrives the username
    }

    private boolean isTokenExpired(String token){
        Date expiration = Jwts.parser()
                .verifyWith((SecretKey) getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        return expiration.before(new Date());
    }

    public boolean validateToken(String token, UserDetails userDetails){
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

}