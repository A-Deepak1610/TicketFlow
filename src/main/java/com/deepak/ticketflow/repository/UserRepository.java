package com.deepak.ticketflow.repository;

import com.deepak.ticketflow.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User,Integer> {
    boolean existsByUserName(String userName);
    User findByUserName(String userName);
}
