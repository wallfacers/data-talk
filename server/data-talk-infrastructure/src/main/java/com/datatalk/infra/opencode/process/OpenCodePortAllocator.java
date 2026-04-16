package com.datatalk.infra.opencode.process;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Allocates an available TCP port by probing sequentially from a base port.
 */
public class OpenCodePortAllocator {

    /**
     * Find the first available port starting from basePort.
     *
     * @param basePort  starting port number
     * @param maxRetries maximum number of ports to probe
     * @return an available port number
     * @throws IllegalStateException if no port is available within maxRetries
     */
    public int allocate(int basePort, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            int port = basePort + i;
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IllegalStateException(
            "No available port found in range [" + basePort + "-" + (basePort + maxRetries) + "]");
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}