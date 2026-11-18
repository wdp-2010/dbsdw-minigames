package be.dbsdw.minigames.travel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReachabilityTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void listeningPortIsReachable() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertTrue(Reachability.isReachable("127.0.0.1", server.getLocalPort(), TIMEOUT));
        }
    }

    @Test
    void closedPortIsUnreachable() throws IOException {
        int port;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = server.getLocalPort();
        }
        int closedPort = port;
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertFalse(Reachability.isReachable("127.0.0.1", closedPort, TIMEOUT)));
    }
}
