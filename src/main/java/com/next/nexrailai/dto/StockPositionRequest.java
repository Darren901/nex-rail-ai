package com.next.nexrailai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockPositionRequest(

    @NotBlank(message = "股票代碼不可為空")
    @Pattern(regexp = "^[A-Z]{1,10}$", message = "股票代碼格式錯誤（大寫字母，最多10字元）")
    String symbol,

    @NotNull(message = "股數不可為空")
    @DecimalMin(value = "0.0001", message = "股數必須大於 0")
    BigDecimal shares,

    @NotNull(message = "成本價不可為空")
    @DecimalMin(value = "0.0001", message = "成本價必須大於 0")
    BigDecimal costPrice,

    @Size(max = 255, message = "備註不可超過 255 字元")
    String note
) {}
