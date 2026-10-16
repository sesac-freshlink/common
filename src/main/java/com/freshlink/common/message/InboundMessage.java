package com.freshlink.common.message;

import java.time.OffsetDateTime;
import java.util.List;

/** INBOUND: 물류센터가 점포에 보낸 입고. logistics-simulator → inventory-commands.fifo */
public record InboundMessage(
        String messageId,
        OffsetDateTime createdAt,
        Long storeId,
        String shipmentId,
        List<InboundItem> items
) implements BaseMessage {

    public InboundMessage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public MessageType type() {
        return MessageType.INBOUND;
    }

    public static InboundMessage of(Long storeId, String shipmentId, List<InboundItem> items) {
        return new InboundMessage(BaseMessage.newMessageId(), BaseMessage.now(), storeId, shipmentId, items);
    }
}
