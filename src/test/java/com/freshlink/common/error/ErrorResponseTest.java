package com.freshlink.common.error;

import com.freshlink.common.message.MessageJson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    enum SampleErrorCode implements ErrorCode {
        LOT_NOT_IN_STORE(403, "다른 점포의 lot입니다");

        private final int status;
        private final String message;

        SampleErrorCode(int status, String message) {
            this.status = status;
            this.message = message;
        }

        @Override
        public int status() {
            return status;
        }

        @Override
        public String message() {
            return message;
        }
    }

    @Test
    void 오류_응답은_code_message_traceId_세_키로_나간다() {
        String json = MessageJson.toJson(new ErrorResponse("LOT_NOT_IN_STORE", "다른 점포의 lot입니다", "4bf92f3577b34da6"));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = MessageJson.fromJson(json, Map.class);
        assertThat(map).containsOnlyKeys("code", "message", "traceId");
        assertThat(map).containsEntry("code", "LOT_NOT_IN_STORE")
                .containsEntry("message", "다른 점포의 lot입니다")
                .containsEntry("traceId", "4bf92f3577b34da6");
    }

    @Test
    void enum_오류_코드는_이름이_code다() {
        BusinessException e = new BusinessException(SampleErrorCode.LOT_NOT_IN_STORE);

        assertThat(e.code()).isEqualTo("LOT_NOT_IN_STORE");
        assertThat(e.status()).isEqualTo(403);
        assertThat(e.getMessage()).isEqualTo("다른 점포의 lot입니다");
        assertThat(ErrorResponse.of(e, null)).isEqualTo(new ErrorResponse("LOT_NOT_IN_STORE", "다른 점포의 lot입니다", ""));
    }

    @Test
    void 다른_서비스의_오류_응답을_되던질_수_있다() {
        ErrorResponse remote = MessageJson.fromJson("{\"code\":\"MEMBER_NOT_FOUND\",\"message\":\"회원이 없습니다\",\"traceId\":\"t\"}", ErrorResponse.class);

        BusinessException e = new BusinessException(remote.toErrorCode(404));

        assertThat(e.code()).isEqualTo("MEMBER_NOT_FOUND");
        assertThat(e.status()).isEqualTo(404);
        assertThat(e.getMessage()).isEqualTo("회원이 없습니다");
    }
}
