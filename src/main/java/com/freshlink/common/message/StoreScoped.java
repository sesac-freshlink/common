package com.freshlink.common.message;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 점포 단위로 처리되는 쪽지. 모든 쪽지가 storeId를 가진다.
 * FIFO 큐의 메시지 그룹 ID는 storeId를 문자열로 바꾼 값이다. 점포 안에서는 순서가 지켜지고, 점포끼리는 병렬로 처리된다.
 */
public interface StoreScoped {

    Long storeId();

    /** SQS FIFO MessageGroupId. 예: storeId 1 → "1" */
    @JsonIgnore
    default String messageGroupId() {
        return String.valueOf(storeId());
    }
}
