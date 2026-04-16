package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCodePortAllocatorTest {

    private final OpenCodePortAllocator allocator = new OpenCodePortAllocator();

    @Test
    void allocatesFirstAvailablePortWhenFree() {
        // Test with a specific high port range that's likely free
        int port = allocator.allocate(45000, 100);
        assertThat(port).isGreaterThanOrEqualTo(45000);
        assertThat(port).isLessThan(45000 + 100);
    }

    @Test
    void skipsOccupiedPorts() throws Exception {
        // Occupy port 9900
        ServerSocket socket = new ServerSocket(9900);
        try {
            int port = allocator.allocate(9900, 10);
            assertThat(port).isEqualTo(9901);
        } finally {
            socket.close();
        }
    }

    @Test
    void throwsWhenAllPortsOccupied() throws Exception {
        ServerSocket s1 = new ServerSocket(9910);
        ServerSocket s2 = new ServerSocket(9911);
        ServerSocket s3 = new ServerSocket(9912);
        try {
            assertThatThrownBy(() -> allocator.allocate(9910, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No available port");
        } finally {
            s1.close();
            s2.close();
            s3.close();
        }
    }
}