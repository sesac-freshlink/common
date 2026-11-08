package com.freshlink.common.message;

import com.freshlink.common.message.command.*;
import com.freshlink.common.message.notification.NotificationKind;
import com.freshlink.common.message.notification.NotificationMessage;
import com.freshlink.common.message.result.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MessageJsonTest {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-15T12:30:00+09:00");

    static Stream<BaseMessage> allTypes() {
        return Stream.of(
                InboundMessage.of(1L, "SHP-20261015-001", List.of(
                        new InboundItem("APL-001", 30, OffsetDateTime.parse("2026-10-20T23:59:59+09:00")))),
                ReserveMessage.of(1024L, 1L, 15L, 2),
                ReserveMessage.of(1024L, 1L, 15L, 3, 2),
                ConfirmMessage.of(1024L, 1L),
                ReleaseMessage.of(1024L, 1L, ReleaseReason.PAYMENT_FAILED),
                ReleaseMessage.of(1024L, 1L, ReleaseReason.CANCELED),
                ReturnMessage.of(1024L, 1L),
                DisposeMessage.of(7L, 1L, 300L, 5, "c1a2b3c4-0000-4000-8000-000000000001"),
                ReservedMessage.of(1024L, 1L, 15L, "매운 컵라면 115g", 2, 3500L, List.of(
                        new ReservedLot(300L, 2, OffsetDateTime.parse("2026-10-20T23:59:59+09:00")))),
                ReservedMessage.of(1024L, 1L, 15L, "매운 컵라면 115g", 3, 3500L, List.of(
                        ReservedLot.clearance(299L, 2, OffsetDateTime.parse("2026-11-08T23:59:59+09:00"), 2450L),
                        new ReservedLot(300L, 1, OffsetDateTime.parse("2026-11-12T23:59:59+09:00")))),
                ReserveFailedMessage.of(1024L, 1L, 15L, ReserveFailReason.OUT_OF_STOCK, 0),
                ReserveFailedMessage.of(1024L, 1L, 15L, ReserveFailReason.CLEARANCE_SOLD_OUT, 4, 1),
                NotificationMessage.expiryImminent(1L, 15L, "매운 컵라면 115g", 300L,
                        OffsetDateTime.parse("2026-10-31T23:59:59+09:00"), 4),
                NotificationMessage.expired(1L, 15L, "매운 컵라면 115g", 300L,
                        OffsetDateTime.parse("2026-10-30T23:59:59+09:00"), 2),
                NotificationMessage.restocked(1L, 15L, "매운 컵라면 115g", 20),
                NotificationMessage.newOrder(1L, 501L, "FL-20261030-0001", 15L, "매운 컵라면 115g", 2),
                NotificationMessage.pickupReady(1L, "역삼점", "cognito-sub-1", 501L, "FL-20261030-0001",
                        15L, "매운 컵라면 115g", 2)
        );
    }

    @ParameterizedTest
    @MethodSource("allTypes")
    void 객체_JSON_BaseMessage_왕복하면_같은_클래스_같은_값(BaseMessage original) {
        String json = MessageJson.toJson(original);
        BaseMessage read = MessageJson.fromJson(json);

        assertThat(read).isInstanceOf(original.getClass());
        assertThat(read).isEqualTo(original);
        assertThat(json).contains("\"type\":\"" + original.type().name() + "\"");
        assertThat(json.split("\"type\"").length - 1).as("type 키는 한 번만").isEqualTo(1);
        // createdAt 값은 위 isEqualTo로 본다. 문자열로 비교하면 정각 00초일 때 toString이 초를 빼서 틀린다
        assertThat(json).contains("\"createdAt\":\"");
        assertThat(json).contains("+09:00");
    }

    @Test
    void 인수인계_RESERVE_예시_JSON을_읽으면_ReserveMessage() {
        String json = """
                {
                  "messageId": "b3f1c2e0-7a1d-4c55-9e0a-1f2a3b4c5d6e",
                  "type": "RESERVE",
                  "createdAt": "2026-10-15T12:30:00+09:00",
                  "orderId": 1024,
                  "storeId": 1,
                  "productId": 15,
                  "quantity": 1
                }
                """;

        BaseMessage read = MessageJson.fromJson(json);

        assertThat(read).isInstanceOf(ReserveMessage.class);
        ReserveMessage r = (ReserveMessage) read;
        assertThat(r.messageId()).isEqualTo("b3f1c2e0-7a1d-4c55-9e0a-1f2a3b4c5d6e");
        assertThat(r.type()).isEqualTo(MessageType.RESERVE);
        assertThat(r.createdAt()).isEqualTo(T0);
        assertThat(r.orderId()).isEqualTo(1024L);
        assertThat(r.storeId()).isEqualTo(1L);
        assertThat(r.productId()).isEqualTo(15L);
        assertThat(r.quantity()).isEqualTo(1);
    }

    @Test
    void RETURN은_orderId_storeId만_싣고_RELEASE_사유_CANCELED도_문자열로_간다() {
        String ret = MessageJson.toJson(ReturnMessage.of(1024L, 1L));
        assertThat(ret).contains("\"type\":\"RETURN\"", "\"orderId\":1024", "\"storeId\":1");
        assertThat(ret).doesNotContain("productId", "lotId", "quantity");

        String rel = MessageJson.toJson(ReleaseMessage.of(1024L, 1L, ReleaseReason.CANCELED));
        assertThat(rel).contains("\"reason\":\"CANCELED\"");
        assertThat(MessageJson.fromJson(rel, ReleaseMessage.class).reason()).isEqualTo(ReleaseReason.CANCELED);
    }

    @Test
    void 마감세일_칸이_없는_옛_RESERVE는_마감세일_0으로_읽힌다() {
        String json = """
                {"messageId":"m1","type":"RESERVE","createdAt":"2026-10-15T09:00:00+09:00",
                 "orderId":1024,"storeId":1,"productId":15,"quantity":2}
                """;

        ReserveMessage r = (ReserveMessage) MessageJson.fromJson(json);

        assertThat(r.clearanceQuantity()).isZero();
        assertThat(r.regularQuantity()).isEqualTo(2);
        assertThat(r).isEqualTo(new ReserveMessage("m1", OffsetDateTime.parse("2026-10-15T09:00:00+09:00"), 1024L, 1L, 15L, 2));
    }

    @Test
    void 마감세일_RESERVE는_전체_수량과_마감세일_수량을_싣고_기본_수량은_싣지_않는다() {
        ReserveMessage r = ReserveMessage.of(1024L, 1L, 15L, 3, 2);
        String json = MessageJson.toJson(r);

        assertThat(json).contains("\"quantity\":3", "\"clearanceQuantity\":2");
        assertThat(json).doesNotContain("regularQuantity");
        assertThat(r.regularQuantity()).isEqualTo(1);
        // 마감세일만 사는 주문도 된다 (기본 수량 0)
        assertThat(ReserveMessage.of(1024L, 1L, 15L, 2, 2).regularQuantity()).isZero();
    }

    @Test
    void 마감세일_칸이_없는_옛_RESERVED_lot은_기본_lot_단가_null로_읽힌다() {
        String json = """
                {"messageId":"m1","type":"RESERVED","createdAt":"2026-10-15T09:00:00+09:00",
                 "orderId":1024,"storeId":1,"productId":15,"productName":"매운 컵라면 115g","quantity":1,"unitPrice":3500,
                 "lots":[{"lotId":300,"quantity":1,"expiryDate":"2026-10-20"}]}
                """;

        ReservedLot lot = ((ReservedMessage) MessageJson.fromJson(json)).lots().get(0);

        assertThat(lot.clearance()).isFalse();
        assertThat(lot.unitPrice()).isNull();
        assertThat(lot).isEqualTo(new ReservedLot(300L, 1, OffsetDateTime.parse("2026-10-20T23:59:59+09:00")));
    }

    @Test
    void 마감세일_lot은_clearance와_단가를_싣는다() {
        String json = MessageJson.toJson(ReservedMessage.of(1024L, 1L, 15L, "매운 컵라면 115g", 1, 3500L, List.of(
                ReservedLot.clearance(299L, 1, OffsetDateTime.parse("2026-11-08T23:59:59+09:00"), 2450L))));

        assertThat(json).contains("\"clearance\":true", "\"unitPrice\":2450", "\"unitPrice\":3500");
    }

    @Test
    void 옛_RESERVE_FAILED는_마감세일_남은_수량_null_새_사유는_문자열로_간다() {
        String old = """
                {"messageId":"m1","type":"RESERVE_FAILED","createdAt":"2026-10-15T09:00:00+09:00",
                 "orderId":1024,"storeId":1,"productId":15,"reason":"OUT_OF_STOCK","availableQuantity":0}
                """;
        ReserveFailedMessage f = (ReserveFailedMessage) MessageJson.fromJson(old);
        assertThat(f.clearanceAvailableQuantity()).isNull();
        assertThat(f.availableQuantity()).isZero();

        String json = MessageJson.toJson(ReserveFailedMessage.of(1024L, 1L, 15L, ReserveFailReason.CLEARANCE_SOLD_OUT, 4, 0));
        assertThat(json).contains("\"reason\":\"CLEARANCE_SOLD_OUT\"", "\"availableQuantity\":4", "\"clearanceAvailableQuantity\":0");
    }

    @Test
    void 구체_클래스로도_바로_읽힌다() {
        String json = MessageJson.toJson(ConfirmMessage.of(1024L, 1L));
        ConfirmMessage c = MessageJson.fromJson(json, ConfirmMessage.class);
        assertThat(c.orderId()).isEqualTo(1024L);
    }

    @Test
    void 유통기한에_날짜만_오면_그날_235959_한국시간() {
        String json = """
                {"messageId":"m1","type":"INBOUND","createdAt":"2026-10-15T09:00:00+09:00",
                 "storeId":1,"shipmentId":"SHP-1",
                 "items":[{"productCode":"APL-001","quantity":30,"expiryDate":"2026-10-20"}]}
                """;

        InboundMessage m = (InboundMessage) MessageJson.fromJson(json);

        assertThat(m.items().get(0).expiryDate()).isEqualTo(OffsetDateTime.parse("2026-10-20T23:59:59+09:00"));
    }

    @Test
    void 유통기한에_일시가_오면_그대로() {
        String json = """
                {"messageId":"m1","type":"RESERVED","createdAt":"2026-10-15T09:00:00+09:00",
                 "orderId":1024,"storeId":1,"productId":15,"productName":"매운 컵라면 115g","quantity":1,"unitPrice":3500,
                 "lots":[{"lotId":300,"quantity":1,"expiryDate":"2026-10-20T10:00:00+09:00"}]}
                """;

        ReservedMessage m = (ReservedMessage) MessageJson.fromJson(json);

        assertThat(m.lots().get(0).expiryDate()).isEqualTo(OffsetDateTime.parse("2026-10-20T10:00:00+09:00"));
        assertThat(m.productName()).isEqualTo("매운 컵라면 115g");
    }

    @Test
    void 모르는_필드가_섞여_있어도_읽힌다() {
        String json = """
                {"messageId":"m1","type":"CONFIRM","createdAt":"2026-10-15T09:00:00+09:00",
                 "orderId":1024,"storeId":1,"futureField":"x","another":{"a":1}}
                """;

        BaseMessage read = MessageJson.fromJson(json);

        assertThat(read).isInstanceOf(ConfirmMessage.class);
        assertThat(((ConfirmMessage) read).orderId()).isEqualTo(1024L);
    }

    @Test
    void 메시지_그룹_ID는_storeId_문자열_중복제거_ID는_messageId() {
        ReserveMessage m = ReserveMessage.of(1024L, 1L, 15L, 1);

        assertThat(m.messageGroupId()).isEqualTo("1");
        assertThat(m.deduplicationId()).isEqualTo(m.messageId());
        assertThat(MessageJson.toJson(m)).doesNotContain("messageGroupId").doesNotContain("deduplicationId");
    }

    @Test
    void 팩토리는_messageId와_createdAt을_채운다() {
        ReserveMessage a = ReserveMessage.of(1024L, 1L, 15L, 1);
        ReserveMessage b = ReserveMessage.of(1024L, 1L, 15L, 1);

        assertThat(a.messageId()).isNotBlank().isNotEqualTo(b.messageId());
        assertThat(a.createdAt()).isNotNull();
        assertThat(a.createdAt().getOffset()).isEqualTo(MessageJson.KST);
    }

    @Test
    void 인수인계_NOTIFICATION_예시_JSON을_읽으면_NotificationMessage() {
        String json = """
                {"messageId":"6f1c2a9e-0000-4000-8000-000000000001","type":"NOTIFICATION",
                 "createdAt":"2026-10-31T00:00:00+09:00","kind":"EXPIRY_IMMINENT","storeId":1,"userId":null,
                 "productId":15,"productName":"매운 컵라면 115g","lotId":300,
                 "expiresAt":"2026-10-31T23:59:59+09:00","quantity":4}
                """;

        BaseMessage read = MessageJson.fromJson(json);

        assertThat(read).isInstanceOf(NotificationMessage.class);
        NotificationMessage n = (NotificationMessage) read;
        assertThat(n.kind()).isEqualTo(NotificationKind.EXPIRY_IMMINENT);
        assertThat(n.storeId()).isEqualTo(1L);
        assertThat(n.userId()).isNull();
        assertThat(n.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-10-31T23:59:59+09:00"));
        assertThat(n.quantity()).isEqualTo(4);
    }

    @Test
    void RESTOCKED는_lot과_유통기한_없이_간다() {
        NotificationMessage m = NotificationMessage.restocked(1L, 15L, "매운 컵라면 115g", 20);

        assertThat(m.kind()).isEqualTo(NotificationKind.RESTOCKED);
        assertThat(m.lotId()).isNull();
        assertThat(m.expiresAt()).isNull();
        assertThat(MessageJson.toJson(m)).contains("\"kind\":\"RESTOCKED\"", "\"quantity\":20");
    }

    @Test
    void PICKUP_READY는_주문한_고객과_주문을_싣는다() {
        NotificationMessage m = NotificationMessage.pickupReady(1L, "역삼점", "cognito-sub-1", 501L,
                "FL-20261030-0001", 15L, "매운 컵라면 115g", 2);

        NotificationMessage read = (NotificationMessage) MessageJson.fromJson(MessageJson.toJson(m));

        assertThat(read.kind()).isEqualTo(NotificationKind.PICKUP_READY);
        assertThat(read.userId()).isEqualTo("cognito-sub-1");
        assertThat(read.orderId()).isEqualTo(501L);
        assertThat(read.orderNo()).isEqualTo("FL-20261030-0001");
        assertThat(read.storeName()).isEqualTo("역삼점");
    }

    @Test
    void 주문_칸이_없는_v0_6_0_JSON도_읽는다() {
        String json = """
                {"messageId":"6f1c2a9e-0000-4000-8000-000000000002","type":"NOTIFICATION",
                 "createdAt":"2026-10-31T00:00:00+09:00","kind":"RESTOCKED","storeId":1,
                 "productId":15,"productName":"매운 컵라면 115g","quantity":20}
                """;

        NotificationMessage read = (NotificationMessage) MessageJson.fromJson(json);

        assertThat(read.orderId()).isNull();
        assertThat(read.storeName()).isNull();
    }
}
