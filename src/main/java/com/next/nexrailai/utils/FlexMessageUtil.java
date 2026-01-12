package com.next.nexrailai.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linecorp.bot.messaging.model.*;
import com.next.nexrailai.dto.FareResultDTO;
import com.next.nexrailai.dto.ThsrSummaryDTO;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.entity.UserMemory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FlexMessageUtil {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 建立任務列表卡片 (提醒或監控)
     */
    public static FlexMessage createTaskListBubble(String title, List<ScheduleTask> tasks) {
        FlexBox header = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder().text(title).weight(FlexText.Weight.BOLD).color("#ffffff").size("md").build()
        )).backgroundColor("#464E51").build();

        List<FlexComponent> bodyContents = new ArrayList<>();

        if (tasks == null || tasks.isEmpty()) {
            bodyContents.add(new FlexText.Builder().text("目前沒有進行中的任務喔！").size("sm").color("#aaaaaa").margin("md").build());
        } else {
            for (ScheduleTask task : tasks) {
                bodyContents.add(createTaskRow(task));
                bodyContents.add(new FlexSeparator.Builder().margin("md").build());
            }
            // 移除最後一個分隔線
            bodyContents.remove(bodyContents.size() - 1);
        }

        FlexBox body = new FlexBox.Builder(FlexBox.Layout.VERTICAL, bodyContents).spacing("md").build();

        FlexBubble bubble = new FlexBubble.Builder()
                .header(header)
                .body(body)
                .size(FlexBubble.Size.MEGA)
                .build();

        return new FlexMessage(title + "列表", bubble);
    }

    private static FlexBox createTaskRow(ScheduleTask task) {
        String timeStr = task.getTriggerTime().format(DATE_TIME_FORMATTER);
        String desc = task.getContent();

        return new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                        new FlexText.Builder().text(timeStr).size("xs").color("#FF6B00").weight(FlexText.Weight.BOLD).build(),
                        new FlexText.Builder().text(desc).size("sm").weight(FlexText.Weight.BOLD).wrap(true).build()
                )).flex(4).build(),
                new FlexButton.Builder(new PostbackAction.Builder()
                        .label("×")
                        .data("action=cancel_task&id=" + task.getId())
                        .displayText("取消任務：" + desc)
                        .build())
                        .style(FlexButton.Style.SECONDARY)
                        .height(FlexButton.Height.SM)
                        .flex(1)
                        .build()
        )).alignItems(FlexBox.AlignItems.CENTER).margin("md").build();
    }

    /**
     * 建立常用行程列表卡片
     */
    public static FlexMessage createMemoryListBubble(List<UserMemory> memories) {
        FlexBox header = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder().text("常用行程管理").weight(FlexText.Weight.BOLD).color("#ffffff").size("md").build()
        )).backgroundColor("#0081C6").build();

        List<FlexComponent> bodyContents = new ArrayList<>();

        if (memories == null || memories.isEmpty()) {
            bodyContents.add(new FlexText.Builder().text("目前還沒有儲存任何行程喔！\n您可以對我說：『記住這班車』").size("sm").color("#aaaaaa").margin("md").wrap(true).build());
        } else {
            for (UserMemory memory : memories) {
                bodyContents.add(createMemoryRow(memory));
                bodyContents.add(new FlexSeparator.Builder().margin("md").build());
            }
            bodyContents.remove(bodyContents.size() - 1);
        }

        FlexBox body = new FlexBox.Builder(FlexBox.Layout.VERTICAL, bodyContents).spacing("md").build();

        FlexBubble bubble = new FlexBubble.Builder()
                .header(header)
                .body(body)
                .size(FlexBubble.Size.MEGA)
                .build();

        return new FlexMessage("常用行程列表", bubble);
    }

    private static FlexBox createMemoryRow(UserMemory memory) {
        String displayText;
        try {
            Map<String, Object> data = JsonUtil.fromJson(memory.getMemoryValue(), new TypeReference<>() {});
            if (data == null) {
                displayText = memory.getMemoryValue();
            } else {
                String from = (String) data.getOrDefault("from", "");
                String to = (String) data.getOrDefault("to", "");
                String trainNo = (String) data.getOrDefault("trainNumber", "");
                String date = (String) data.getOrDefault("trainDate", "");
                String time = (String) data.getOrDefault("trainTime", "");
                
                if (trainNo != null && !trainNo.isEmpty()) {
                    displayText = String.format("%s ➔ %s (車次 %s)\n%s %s", from, to, trainNo, date, time);
                } else {
                    displayText = String.format("%s ➔ %s\n%s %s", from, to, date, time);
                }
            }
        } catch (Exception e) {
            displayText = memory.getMemoryValue(); // Fallback
        }

        return new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                        new FlexText.Builder().text(memory.getMemoryKey()).size("md").weight(FlexText.Weight.BOLD).build(),
                        new FlexText.Builder().text(displayText).size("xs").color("#888888").wrap(true).build()
                )).flex(3).build(),
                new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                        new FlexButton.Builder(new PostbackAction.Builder()
                                .label("⌕")
                                .data("action=use_memory&id=" + memory.getId())
                                .displayText("使用行程：" + memory.getMemoryKey())
                                .build())
                                .style(FlexButton.Style.PRIMARY)
                                .height(FlexButton.Height.SM)
                                .color("#0081C6")
                                .build(),
                        new FlexButton.Builder(new PostbackAction.Builder()
                                .label("×")
                                .data("action=delete_memory&id=" + memory.getId())
                                .displayText("刪除行程：" + memory.getMemoryKey())
                                .build())
                                .style(FlexButton.Style.SECONDARY)
                                .height(FlexButton.Height.SM)
                                .margin("xs")
                                .build()
                )).flex(2).build()
        )).alignItems(FlexBox.AlignItems.CENTER).margin("md").build();
    }

    /**
     * 將高鐵班次列表轉換為 LINE Flex Carousel 訊息
     */
    public static FlexMessage createTimetableCarousel(List<ThsrSummaryDTO> trains, String origin, String destination, String trainDate) {
        List<FlexBubble> bubbles = trains.stream()
                .map(train -> createTrainBubble(train, origin, destination, trainDate))
                .collect(Collectors.toList());

        FlexCarousel carousel = new FlexCarousel(bubbles);

        return new FlexMessage("高鐵時刻表查詢結果", carousel);
    }

    private static FlexBubble createTrainBubble(ThsrSummaryDTO train, String origin, String destination, String trainDate) {
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

                // 日期 Row
                new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                        new FlexText.Builder().text(trainDate).size("xs").color("#888888").build()
                )).margin("sm").build(),

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
                new FlexButton.Builder(new MessageAction("我要訂這班", "幫我訂 " + train.trainNo() + " 車次"))
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



    /**
     * 建立訂票確認卡片
     */
    public static FlexMessage createBookingConfirmationBubble(String baseUrl, String link, String origin, String dest, String date, String time, String trainNo) {
        
        // 生成中轉網址，並加上 openExternalBrowser=1 強迫 LINE 使用外部瀏覽器開啟
        String redirectUrl = baseUrl + "/api/redirect?url=" + URLEncoder.encode(link, StandardCharsets.UTF_8) + "&openExternalBrowser=1";

        // Header
        FlexBox header = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder().text("確認訂票資訊").weight(FlexText.Weight.BOLD).color("#ffffff").size("md").build()
        )).backgroundColor("#00B900").build(); // 使用綠色或其他顏色區分

        // Body
        FlexBox body = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                // 日期時間
                new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                        new FlexText.Builder().text(date).size("sm").color("#555555").build(),
                        new FlexText.Builder().text(time + " 出發").size("xl").weight(FlexText.Weight.BOLD).margin("sm").build()
                )).build(),
                
                new FlexSeparator.Builder().margin("md").build(),

                // 車次與地點
                new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                        new FlexText.Builder().text("車次 " + trainNo).size("sm").color("#aaaaaa").flex(0).build(),
                        new FlexText.Builder().text(origin + " ➔ " + dest).size("sm").weight(FlexText.Weight.BOLD).align(FlexText.Align.END).build()
                )).margin("md").build()
        )).build();

        // Footer: 前往訂票按鈕
        FlexBox footer = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexButton.Builder(new URIAction.Builder()
                        .label("跳轉至 T-EX 行動購票")
                        .uri(URI.create(redirectUrl)) // 使用中轉網址
                        .build())
                        .style(FlexButton.Style.PRIMARY)
                        .height(FlexButton.Height.SM)
                        .color("#00B900")
                        .build(),
                // 增加提示文字
                new FlexText.Builder()
                        .text("💡 本系統資料來自 API，可能存在極短時間差。若跳轉後顯示客滿，請在 APP 中多按幾次查詢按鈕重新整理即可。")
                        .size("xxs")
                        .color("#aaaaaa")
                        .margin("md")
                        .wrap(true)
                        .build()
        )).spacing("sm").flex(0).build();

        FlexBubble bubble = new FlexBubble.Builder()
                .header(header)
                .body(body)
                .footer(footer)
                .size(FlexBubble.Size.MEGA)
                .build();

        return new FlexMessage("訂票連結已產生", bubble);
    }

    private static FlexBox createSeatStatusRow(String label, String status) {

        String color = "#28a745"; // 綠 (有位)
        if (status.contains("客滿") || status.contains("已過售票")) {
            color = "#dc3545"; // 紅 (客滿)
        } else if (status.contains("不多") || status.contains("緊張")) {
            color = "#ffc107"; // 黃 (緊張)
        }

        // 圓形狀態燈
        FlexBox statusDot = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of())
                .width("12px")
                .height("12px")
                .cornerRadius("6px") // 圓形
                .backgroundColor(color)
                .build();

        return new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                statusDot,
                new FlexText.Builder()
                        .text(label + " " + status)
                        .size("sm")
                        .color("#555555")
                        .margin("md") // 圓點與文字的間距
                        .flex(1)
                        .build()
        ))
                .alignItems(FlexBox.AlignItems.CENTER) // 垂直置中
                .margin("sm")
                .build();
    }

    /**
     * 建立票價資訊卡片
     */
    public static FlexMessage createPriceInfoBubble(String origin, String destination, List<com.next.nexrailai.dto.FareResultDTO> fares) {
        // Header
        FlexBox header = new FlexBox.Builder(FlexBox.Layout.VERTICAL, List.of(
                new FlexText.Builder()
                        .text("票價一覽")
                        .weight(FlexText.Weight.BOLD)
                        .color("#ffffff")
                        .size("md")
                        .build()
        ))
                .backgroundColor("#0081C6") // 藍色
                .build();

        // Body components
        List<FlexComponent> bodyComponents = new java.util.ArrayList<>();

        // Title: Origin -> Destination
        bodyComponents.add(new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                new FlexText.Builder().text(origin).size("xl").weight(FlexText.Weight.BOLD).align(FlexText.Align.END).flex(4).build(),
                new FlexText.Builder().text("➔").size("md").color("#aaaaaa").align(FlexText.Align.CENTER).flex(2).build(),
                new FlexText.Builder().text(destination).size("xl").weight(FlexText.Weight.BOLD).align(FlexText.Align.START).flex(4).build()
        )).build());

        bodyComponents.add(new FlexSeparator.Builder().margin("md").build());

        // 自由座
        FlexBox freeBlock = createCabinPriceBlock("自由座", fares);
        if (freeBlock != null) {
            bodyComponents.add(freeBlock);
            bodyComponents.add(new FlexSeparator.Builder().margin("md").build());
        }

        // 標準座
        FlexBox standardBlock = createCabinPriceBlock("標準座", fares);
        if (standardBlock != null) {
            bodyComponents.add(standardBlock);
            bodyComponents.add(new FlexSeparator.Builder().margin("md").build());
        }

        // 商務座
        FlexBox businessBlock = createCabinPriceBlock("商務座", fares);
        if (businessBlock != null) {
            bodyComponents.add(businessBlock);
        }

        FlexBox body = new FlexBox.Builder(FlexBox.Layout.VERTICAL, bodyComponents).build();

        FlexBubble bubble = new FlexBubble.Builder()
                .header(header)
                .body(body)
                .size(FlexBubble.Size.MEGA) // 使用較寬的卡片
                .build();

        return new FlexMessage("票價查詢結果", bubble);
    }

    private static FlexBox createCabinPriceBlock(String cabinName, List<FareResultDTO> fares) {
        // 篩選出該艙等的票價
        List<FareResultDTO> cabinFares = fares.stream()
                .filter(f -> f.cabinClass().equals(cabinName))
                .collect(Collectors.toList());

        if (cabinFares.isEmpty()) return null;

        // 標題 Row (例如: "標準座")
        List<FlexComponent> rows = new java.util.ArrayList<>();
        rows.add(new FlexText.Builder()
                .text(cabinName)
                .weight(FlexText.Weight.BOLD)
                .size("md")
                .color("#111111")
                .margin("md")
                .build());

        // 內容 Rows
        for (FareResultDTO fare : cabinFares) {
             // 1. 過濾不需要顯示的票種 (例如團體票)
             if ("團體票".equals(fare.ticketType())) continue;

             String label = fare.fareClass();
             String ticketType = fare.ticketType();

             // 2. 處理 Label
             if ("單程票".equals(ticketType)) {
                 if ("成人".equals(label)) {
                     label = "全票";
                 } else if ("孩童".equals(label) || "敬老".equals(label) || "愛心".equals(label)) {
                     label = "優待票 (" + label + ")";
                 } else if ("學生".equals(label)) {
                     label = "大學生優惠";
                 } else {
                     // 其他 (如軍警、法優)
                     label = label + "票";
                 }
             } else {
                 // 非單程票 (如早鳥票)，顯示例如 "早鳥票 (成人)"
                 label = ticketType + " (" + label + ")";
             }

            rows.add(new FlexBox.Builder(FlexBox.Layout.HORIZONTAL, List.of(
                    new FlexText.Builder().text(label).size("sm").color("#666666").flex(0).build(),
                    new FlexText.Builder().text("$" + fare.price()).size("sm").align(FlexText.Align.END).color("#333333").build()
            )).margin("sm").build());
        }

        return new FlexBox.Builder(FlexBox.Layout.VERTICAL, rows).build();
    }
}