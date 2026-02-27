package com.next.nexrailai.controller.admin;

import com.next.nexrailai.dto.StockPositionRequest;
import com.next.nexrailai.dto.StockPositionResponse;
import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.repository.StockPositionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
@Slf4j
public class StockAdminController {

    private final StockPositionRepository stockPositionRepository;

    @GetMapping("/positions")
    public ResponseEntity<List<StockPositionResponse>> getAllPositions() {
        List<StockPositionResponse> positions = stockPositionRepository.findAllByOrderBySymbolAsc()
            .stream()
            .map(StockPositionResponse::from)
            .toList();
        return ResponseEntity.ok(positions);
    }

    @PostMapping("/positions")
    public ResponseEntity<StockPositionResponse> createPosition(@Valid @RequestBody StockPositionRequest request) {
        log.info(">>>> [Stock Admin] 新增持倉: {}", request.symbol());
        if (stockPositionRepository.existsBySymbol(request.symbol())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        StockPosition position = StockPosition.builder()
            .symbol(request.symbol().toUpperCase())
            .shares(request.shares())
            .costPrice(request.costPrice())
            .note(request.note())
            .build();
        return ResponseEntity.ok(StockPositionResponse.from(stockPositionRepository.save(position)));
    }

    @PutMapping("/positions/{symbol}")
    public ResponseEntity<StockPositionResponse> updatePosition(
            @PathVariable String symbol,
            @Valid @RequestBody StockPositionRequest request) {
        log.info(">>>> [Stock Admin] 更新持倉: {}", symbol);
        return stockPositionRepository.findBySymbol(symbol.toUpperCase())
            .map(position -> {
                position.setShares(request.shares());
                position.setCostPrice(request.costPrice());
                position.setNote(request.note());
                return ResponseEntity.ok(StockPositionResponse.from(stockPositionRepository.save(position)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/positions/{symbol}")
    public ResponseEntity<Void> deletePosition(@PathVariable String symbol) {
        log.info(">>>> [Stock Admin] 刪除持倉: {}", symbol);
        return stockPositionRepository.findBySymbol(symbol.toUpperCase())
            .map(position -> {
                stockPositionRepository.delete(position);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
