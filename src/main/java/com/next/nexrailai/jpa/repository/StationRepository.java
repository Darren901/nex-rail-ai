package com.next.nexrailai.jpa.repository;


import com.next.nexrailai.jpa.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByStationNameContaining(String name);
}
