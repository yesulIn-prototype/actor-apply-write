package kr.yesulin.actor.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompletedFileNameTest {
    @Test
    void defaultFor_DoesNotDuplicateCompletionSuffix_WhenOriginalIsAlreadyCompleted() {
        assertThat(CompletedFileName.defaultFor("공부의신_완성.hwp").hwp())
                .isEqualTo("공부의신_완성.hwp");
    }
}
