package com.datatalk.domain.action;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorTest {

    @Test
    void hasExactlyThreeValuesInDeclaredOrder() {
        assertThat(Executor.values())
            .containsExactly(Executor.OPENCODE, Executor.SERVER, Executor.CLIENT);
    }
}
