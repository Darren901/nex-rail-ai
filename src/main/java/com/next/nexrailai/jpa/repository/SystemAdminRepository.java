package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.SystemAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemAdminRepository extends JpaRepository<SystemAdmin, Long> {
    Optional<SystemAdmin> findByUsername(String username);
}
