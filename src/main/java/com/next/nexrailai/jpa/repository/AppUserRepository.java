package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    List<AppUser> findAllByStatus(AppUser.UserStatus status);
}
