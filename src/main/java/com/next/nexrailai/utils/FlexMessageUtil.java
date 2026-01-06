package com.next.nexrailai.utils;

import com.linecorp.bot.messaging.model.*;
import com.next.nexrailai.dto.ThsrSummaryDTO;

import java.util.List;
import java.util.stream.Collectors;

public class FlexMessageUtil {

    /**
     * 將高鐵班次列表轉換為 LINE Flex Carousel 訊息
     */
    public static FlexMessage createTimetableCarousel(List<ThsrSummaryDTO> trains, String origin, String destination) {
        List<FlexBubble> bubbles = trains.stream()
                .map(train -> createTrainBubble(train, origin, destination))
                .collect(Collectors.toList());

        FlexCarousel carousel = new FlexCarousel(bubbles);

        return new FlexMessage("高鐵時刻表查詢結果", carousel);
    }

    private static FlexBubble createTrainBubble(ThsrSummaryDTO train, String origin, String destination) {
        // Header: 車次號碼
        FlexBox header = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder()
                        .text("車次 " + train.trainNo())
                        .weight(FlexText.Weight.BOLD)
                        .color("#ffffff")
                        .size("sm")
                        .build()
        ))
                .backgroundColor("#FF6B00")
                .build();

        // Body
        FlexBox body = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                // 站名 Row
                new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                        new FlexText.Builder().text(origin).size("xl").weight(FlexText.Weight.BOLD).flex(0).build(),
                        new FlexText.Builder().text("➔").gravity(FlexText.Gravity.CENTER).align(FlexText.Align.CENTER).size("sm").color("#aaaaaa").build(),
                        new FlexText.Builder().text(destination).size("xl").weight(FlexText.Weight.BOLD).flex(0).build()
                )).build(),

                // 時間 Row
                new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                        new FlexText.Builder().text(train.departureTime()).size("sm").color("#555555").build(),
                        new FlexText.Builder().text(train.arrivalTime()).size("sm").color("#555555").align(FlexText.Align.END).build()
                )).margin("md").build(),

                new FlexSeparator.Builder().margin("lg").build(),

                // 座位 Row
                new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                        createSeatStatusRow("標準座", train.standardSeatStatus()),
                        createSeatStatusRow("商務座", train.businessSeatStatus())
                )).margin("lg").build()
        )).build();

        // Footer: 按鈕
        FlexBox footer = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexButton.Builder(new MessageAction("我要訂這班", "那幫我訂 " + train.trainNo() + " 車次"))
                        .style(FlexButton.Style.PRIMARY)
                        .height(FlexButton.Height.SM)
                        .color("#FF6B00")
                        .build(),
                new FlexButton.Builder(new MessageAction("查詳細票價", "查 " + train.trainNo() + " 的票價資訊"))
                        .style(FlexButton.Style.SECONDARY)
                        .height(FlexButton.Height.SM)
                        .build()
        ))
                .spacing("sm")
                .flex(0)
                .build();

        // Carousel 中的 Bubble 不能設定 size
        return new FlexBubble.Builder()
                .header(header)
                .body(body)
                .footer(footer)
                .build();
    }

    private static FlexBox createSeatStatusRow(String label, String status) {
        String color = "#28a745"; // 綠
        String icon = "🟢 ";

        if (status.contains("客滿") || status.contains("已過售票")) {
            color = "#dc3545"; // 紅
            icon = "🔴 ";
        } else if (status.contains("不多") || status.contains("緊張")) {
            color = "#ffc107"; // 黃
            icon = "🟡 ";
        }

        return new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                new FlexText.Builder().text(label).size("sm").color("#666666").build(),
                new FlexText.Builder()
                        .text(icon + status)
                        .size("sm")
                        .weight(FlexText.Weight.BOLD)
                        .color(color)
                        .align(FlexText.Align.END)
                        .build()
        )).margin("sm").build();
    }
}