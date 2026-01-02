package com.next.nexrailai.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @author darren
 * @date 2025/12/31
 */
@Component
@Slf4j
public class JsonUtil {

	private static ObjectMapper objectMapper;

	@Autowired
	public void setObjectMapper(ObjectMapper objectMapper) {
		JsonUtil.objectMapper = objectMapper;
	}

	/**
	 * 將物件轉成一個好看的 Json 字串
	 * 
	 **/
	public static String prettyJson(Object obj) {
		try {
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
		} catch (JsonProcessingException e) {
			log.error("Failed to convert object to pretty JSON: " + e.getMessage());
			return "{}";
		}
	}
	
	/**
	 * 將物件轉成一個單純的 Json 字串
	 * 
	 **/
	public static String toJson(Object obj) {
	    try {
	        return objectMapper.writeValueAsString(obj);
	    } catch (JsonProcessingException e) {
	        log.error("Failed to convert object to JSON: " + e.getMessage());
	        return "{}";
	    }
	}

	/**
	 * 將 Json 字串轉回 Java 物件
	 * 
	 **/
	public static <T> T fromJson(String json, Class<T> clazz) {
		try {
			return objectMapper.readValue(json, clazz);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse JSON: " + e.getMessage());
			return null;
		}
	}

	/**
	 * 將 Json 字串轉回 Java 物件 (適用於泛型類型)
	 * 
	 * @param json    需解析的 JSON 字串
	 * @param typeRef TypeReference 實例，攜帶完整的泛型資訊
	 **/
	public static <T> T fromJson(String json, TypeReference<T> typeRef) {
		try {
			return objectMapper.readValue(json, typeRef);
		} catch (JsonProcessingException e) {
			log.error("Failed to parse JSON: " + e.getMessage());
			return null;
		}
	}

//	/**
//	 * 將 Json 字串轉回 Java 物件 (適用於Spring Boot 的泛型物件)
//	 *
//	 * @param json    需解析的 JSON 字串
//	 * @param typeRef ParameterizedTypeReference 實例，攜帶完整的泛型資訊
//	 **/
//	public static <T> T fromJson(
//			String json,
//			ParameterizedTypeReference<T> typeRef
//	) {
//		try {
//			JavaType javaType = objectMapper
//					.getTypeFactory()
//					.constructType(typeRef.getType());
//
//			return objectMapper.readValue(json, javaType);
//		} catch (JsonProcessingException e) {
//			log.error("Failed to parse JSON: {}", e.getMessage(), e);
//			return null;
//		}
//	}

	public static <T> T fromJson(
			String json,
			ParameterizedTypeReference<T> typeRef
	) {
		log.debug(">>> [JsonUtil] fromJson 開始");
		log.debug(">>> [JsonUtil] JSON is null: {}", json == null);
		log.debug(">>> [JsonUtil] typeRef: {}", typeRef.getType());

		try {
			log.debug(">>> [JsonUtil] 準備構造 JavaType");
			JavaType javaType = objectMapper
					.getTypeFactory()
					.constructType(typeRef.getType());

			log.debug(">>> [JsonUtil] JavaType 構造完成: {}", javaType);
			log.debug(">>> [JsonUtil] 準備呼叫 objectMapper.readValue");

			T result = objectMapper.readValue(json, javaType);

			log.debug(">>> [JsonUtil] readValue 完成，result is null: {}", result == null);
			return result;

		} catch (JsonProcessingException e) {
			log.error(">>> [JsonUtil ERROR] JsonProcessingException: {}", e.getMessage());
			log.error(">>> [JsonUtil ERROR] 完整 stack trace:", e);
			return null;
		} catch (Exception e) {
			log.error(">>> [JsonUtil ERROR] 未預期的異常: {}", e.getMessage());
			log.error(">>> [JsonUtil ERROR] 完整 stack trace:", e);
			return null;
		}
	}
	
	/**
	 * 讀取 File 轉回 Java 物件 (適用於泛型類型)
	 * 
	 * @param file    需解析的 file
	 * @param typeRef TypeReference 實例，攜帶完整的泛型資訊
	 **/
	public static <T> T fromFile(File file, TypeReference<T> typeRef) {
		try {
			return objectMapper.readValue(file, typeRef);
		} catch (IOException e) {
			log.error("Failed to parse JSON: " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * 讀取 InputStream 轉回 Java 物件 (適用於泛型類型)
	 * 
	 * @param is    InputStream
	 * @param typeRef TypeReference 實例，攜帶完整的泛型資訊
	 **/
	public static <T> T fromInputStream(InputStream is, TypeReference<T> typeRef) {
		try (InputStream autoClose = is) {
	        return objectMapper.readValue(autoClose, typeRef);
	    } catch (IOException e) {
	        log.error("Failed to parse JSON", e);
	        return null;
	    }
	}

	/**
	 * 將物件快速轉成 Map
	 * 
	 **/
	public static Map<String, Object> toMap(Object obj) {
		try {
			return objectMapper.readValue(objectMapper.writeValueAsString(obj), new TypeReference<>() {});
		} catch (JsonProcessingException e) {
			log.error("Failed to convert object to Map: " + e.getMessage());
			return Map.of();
		}
	}

}
