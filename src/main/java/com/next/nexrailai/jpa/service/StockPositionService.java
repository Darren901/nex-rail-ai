package com.next.nexrailai.jpa.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.repository.StockPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockPositionService {

    private final StockPositionRepository stockPositionRepository;

    /**
     * 取得所有持倉（依股票代碼升冪排序）
     */
    @Transactional(readOnly = true)
    public List<StockPosition> findAll() {
        return stockPositionRepository.findAllByOrderBySymbolAsc();
    }

    /**
     * 依股票代碼查詢持倉
     */
    @Transactional(readOnly = true)
    public Optional<StockPosition> findBySymbol(String symbol) {
        return stockPositionRepository.findBySymbol(symbol);
    }

    /**
     * 檢查股票代碼是否已存在
     */
    @Transactional(readOnly = true)
    public boolean existsBySymbol(String symbol) {
        return stockPositionRepository.existsBySymbol(symbol);
    }

    /**
     * 儲存（新增或更新）持倉
     */
    @Transactional
    public StockPosition save(StockPosition position) {
        return stockPositionRepository.save(position);
    }

    /**
     * 刪除持倉
     */
    @Transactional
    public void delete(StockPosition position) {
        stockPositionRepository.delete(position);
    }
}
