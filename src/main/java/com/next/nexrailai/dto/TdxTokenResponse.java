package com.next.nexrailai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author darren
 * @date 2025/12/31
 */
public record TdxTokenResponse(
   @JsonProperty("access_token") String accessToken,
   @JsonProperty("expires_in") Integer expiresIn,
   @JsonProperty("token_type") String tokenType
) {}
