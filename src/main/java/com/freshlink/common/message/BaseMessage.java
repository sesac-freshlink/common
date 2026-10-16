package com.freshlink.common.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.freshlink.common.message.command.ConfirmMessage;
import com.freshlink.common.message.command.DisposeMessage;
import com.freshlink.common.message.command.InboundMessage;
import com.freshlink.common.message.command.ReleaseMessage;
import com.freshlink.common.message.command.ReserveMessage;
import com.freshlink.common.message.result.ReserveFailedMessage;
import com.freshlink.common.message.result.ReservedMessage;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 모든 쪽지의 공통 부모. 공통 필드는 messageId, type, createdAt.
 *
 * <p>받는 쪽은 {@code BaseMessage}로 읽기만 하면 {@code type} 값에 맞는 클래스가 나온다.
 * <pre>{@code
 * BaseMessage m = MessageJson.fromJson(body);
 * if (m instanceof ReserveMessage r) { ... }
 * }</pre>
 *
 * <p>모르는 필드가 와도 실패하지 않는다. 나중에 필드를 추가해도 옛 버전이 쪽지를 읽을 수 있다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InboundMessage.class, name = "INBOUND"),
        @JsonSubTypes.Type(value = ReserveMessage.class, name = "RESERVE"),
        @JsonSubTypes.Type(value = ConfirmMessage.class, name = "CONFIRM"),
        @JsonSubTypes.Type(value = ReleaseMessage.class, name = "RELEASE"),
        @JsonSubTypes.Type(value = DisposeMessage.class, name = "DISPOSE"),
        @JsonSubTypes.Type(value = ReservedMessage.class, name = "RESERVED"),
        @JsonSubTypes.Type(value = ReserveFailedMessage.class, name = "RESERVE_FAILED")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public interface BaseMessage extends StoreScoped {

    /** 쪽지마다 새로 만드는 UUID. 받는 쪽은 이 값으로 중복을 거른다 */
    String messageId();

    /** 쪽지 종류. JSON에는 @JsonTypeInfo가 {@code type}으로 써 준다 */
    @JsonIgnore
    MessageType type();

    /** 만든 시각. ISO-8601 +09:00 */
    OffsetDateTime createdAt();

    /** SQS FIFO MessageDeduplicationId. messageId와 같다 */
    @JsonIgnore
    default String deduplicationId() {
        return messageId();
    }

    static String newMessageId() {
        return UUID.randomUUID().toString();
    }

    static OffsetDateTime now() {
        return OffsetDateTime.now(MessageJson.KST).withNano(0);
    }
}
