package be.dbsdw.minigames.travel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/** Checks that a server accepts connections before a player is sent there. */
public final class Reachability {
    private Reachability() {}

    public static boolean isReachable(String host, int port, Duration timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeout.toMillis()));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
