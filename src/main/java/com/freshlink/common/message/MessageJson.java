package com.freshlink.common.message;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * 쪽지 JSON 변환기. 한국 시간(+09:00), ISO-8601 문자열, 모르는 필드 무시로 맞춰져 있다.
 *
 * <p>서비스 안에서는 Spring이 만든 ObjectMapper를 써도 된다 ({@code spring.jackson.time-zone: Asia/Seoul} 설정이 있을 때).
 * 쪽지 규칙(type으로 클래스 고르기, 유통기한 해석, 모르는 필드 무시)은 DTO 애너테이션에 있어서 어느 ObjectMapper로 읽어도 같다.
 */
public final class MessageJson {

    /** 한국 시간 오프셋 */
    public static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private MessageJson() {
    }

    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** type 값에 맞는 쪽지 클래스로 읽는다 */
    public static BaseMessage fromJson(String json) {
        return MAPPER.readValue(json, BaseMessage.class);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
