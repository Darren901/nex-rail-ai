package com.next.nexrailai.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 高鐵行程查詢請求
 */
public record SearchRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("起點車站名稱，例如：'板橋'、'台北'、'左營'。")
        String from,

        @JsonProperty(required = true)
        @JsonPropertyDescription("終點車站名稱，例如：'左營'、'台北'。")
        String to,

        @JsonProperty(required = true)
        @JsonPropertyDescription("查詢日期，格式必須為 YYYY-MM-DD。若使用者說『明天』，請計算出日期。")
        String date,

        @JsonPropertyDescription("出發時間之後，格式為 HH:mm。例如使用者說『下午兩點後』，請轉換為 '14:00'。若無指定則可不傳。")
        String time,

        @JsonPropertyDescription("票種。⚠️強烈建議留空(null)，除非使用者明確指定只看某一種。若留空，將一次回傳所有票種(單程/早鳥等)價格。")
        String ticketType,

        @JsonPropertyDescription("身分/費率。⚠️強烈建議留空(null)，除非使用者明確指定只看某一種。若留空，將一次回傳所有身分(成人/孩童/敬老/法優等)價格。")
        String fareClass,

        @JsonPropertyDescription("艙等。⚠️強烈建議留空(null)，除非使用者明確指定只看某一種。若留空，將一次回傳所有艙等(標準/商務/自由)價格。")
        String cabinClass
) {}
