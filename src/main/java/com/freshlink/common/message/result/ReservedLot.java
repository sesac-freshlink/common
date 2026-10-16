package com.freshlink.common.message.result;

import com.freshlink.common.message.json.ExpiryDateDeserializer;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;

/**
 * RESERVED 쪽지에서 예약된 lot 하나. 유통기한이 빠른 lot부터 잡힌다.
 * expiryDate에 날짜만 오면 그날 23:59:59 한국 시간으로 읽는다.
 */
public record ReservedLot(
        Long lotId,
        int quantity,
        @JsonDeserialize(using = ExpiryDateDeserializer.class) OffsetDateTime expiryDate
) {
}
