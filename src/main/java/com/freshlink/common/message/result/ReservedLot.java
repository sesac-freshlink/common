package com.freshlink.common.message.result;

import com.freshlink.common.message.json.ExpiryDateDeserializer;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;

/**
 * RESERVED 쪽지에서 예약된 lot 하나. 유통기한이 빠른 lot부터 잡힌다.
 * expiryDate에 날짜만 오면 그날 23:59:59 한국 시간으로 읽는다.
 *
 * <p>v0.8.0(마감세일 함께 사기): {@code clearance}는 이 lot을 마감세일 재고에서 잡았는지,
 * {@code unitPrice}는 이 lot의 단가(원)다. 마감세일 lot이면 마감세일 단가가 들어 있고, 기본 lot은 null이어도 되며
 * null이면 쪽지의 {@code ReservedMessage.unitPrice}(기본 단가)로 본다. 칸이 없는 옛 쪽지는 {@code clearance=false}, {@code unitPrice=null}로 읽는다.
 */
public record ReservedLot(
        Long lotId,
        int quantity,
        @JsonDeserialize(using = ExpiryDateDeserializer.class) OffsetDateTime expiryDate,
        @JsonSetter(nulls = Nulls.AS_EMPTY) boolean clearance,
        Long unitPrice
) {

    /** v0.7.x까지의 모양. 기본 lot, 단가는 쪽지의 기본 단가 */
    public ReservedLot(Long lotId, int quantity, OffsetDateTime expiryDate) {
        this(lotId, quantity, expiryDate, false, null);
    }

    /** 마감세일 lot. unitPrice = 마감세일 단가 */
    public static ReservedLot clearance(Long lotId, int quantity, OffsetDateTime expiryDate, long unitPrice) {
        return new ReservedLot(lotId, quantity, expiryDate, true, unitPrice);
    }
}
