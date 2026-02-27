package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.StockPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockPositionRepository extends JpaRepository<StockPosition, Long> {

    Optional<StockPosition> findBySymbol(String symbol);

    boolean existsBySymbol(String symbol);

    List<StockPosition> findAllByOrderBySymbolAsc();
}
