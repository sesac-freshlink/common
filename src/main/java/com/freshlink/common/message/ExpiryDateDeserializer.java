package com.freshlink.common.message;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 유통기한 읽기 규칙.
 * <ul>
 *   <li>날짜만 오면 ({@code "2026-10-20"}) 그날 23:59:59 한국 시간 → {@code 2026-10-20T23:59:59+09:00}</li>
 *   <li>일시가 오면 ({@code "2026-10-20T10:00:00+09:00"}) 그대로</li>
 *   <li>오프셋 없는 일시가 오면 ({@code "2026-10-20T10:00:00"}) 한국 시간으로 본다</li>
 * </ul>
 */
public class ExpiryDateDeserializer extends ValueDeserializer<OffsetDateTime> {

    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
        return parse(p.getValueAsString());
    }

    public static OffsetDateTime parse(String text) {
        String value = text.trim();
        if (DATE_ONLY.matcher(value).matches()) {
            return LocalDate.parse(value).atTime(END_OF_DAY).atOffset(MessageJson.KST);
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDateTime.parse(value).atOffset(MessageJson.KST);
        }
    }
}
