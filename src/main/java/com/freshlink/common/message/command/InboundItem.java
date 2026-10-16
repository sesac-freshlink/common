package com.freshlink.common.message.command;

import com.freshlink.common.message.json.ExpiryDateDeserializer;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;

/**
 * INBOUND 쪽지의 입고 품목.
 * expiryDate에 날짜만 오면({@code "2026-10-20"}) 그날 23:59:59 한국 시간으로 읽는다.
 */
public record InboundItem(
        String productCode,
        int quantity,
        @JsonDeserialize(using = ExpiryDateDeserializer.class) OffsetDateTime expiryDate
) {
}
