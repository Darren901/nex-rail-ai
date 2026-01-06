package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
}
