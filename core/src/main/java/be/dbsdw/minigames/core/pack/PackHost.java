package be.dbsdw.minigames.core.pack;

import be.dbsdw.minigames.core.Messages;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;

/**
 * Serves the required resource pack over HTTP (JDK built-in server) at a URL that contains its
 * SHA-1, so clients never use a stale copy, and sends it to arriving players.
 */
public final class PackHost {
    private static final UUID PACK_ID = UUID.fromString("5a2b6c1e-0d8f-4f6b-9a51-6d3b5c7e2f10");

    private final byte[] zip;
    private final String sha1;
    private final URI uri;
    private final Messages messages;
    private final HttpServer server;

    private PackHost(byte[] zip, String sha1, URI uri, Messages messages, HttpServer server) {
        this.zip = zip;
        this.sha1 = sha1;
        this.uri = uri;
        this.messages = messages;
        this.server = server;
    }

    /**
     * Starts hosting {@code file}. Returns null (and logs why) when the pack is not there yet or
     * hosting cannot start; joins then go ahead without a pack.
     */
    public static PackHost start(Path file, String bind, int port, String publicUrl, Messages messages, Logger log) {
        if (!Files.isRegularFile(file)) {
            log.warning("[pack] no resource pack at " + file + " - players join without one");
            return null;
        }
        try {
            byte[] zip = Files.readAllBytes(file);
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(zip));
            String path = "/" + sha1 + ".zip";
            HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 16);
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals(path)) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, zip.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(zip);
                    }
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "MinigameCore-pack-http");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            URI uri = URI.create(publicUrl.replaceAll("/+$", "") + path);
            log.info("[pack] serving " + file.getFileName() + " (" + zip.length / 1024 + " KiB, sha1 " + sha1
                    + ") on port " + port + " as " + uri);
            return new PackHost(zip, sha1, uri, messages, server);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException e) {
            log.severe("[pack] cannot host the resource pack: " + e.getMessage() + " - players join without one");
            return null;
        }
    }

    /**
     * Sends the required pack to a connecting player and waits for the result (configuration
     * phase, async thread). Returns the final status, or null on timeout.
     */
    public ResourcePackStatus sendAndWait(Audience audience, long timeoutSeconds) {
        CompletableFuture<ResourcePackStatus> result = new CompletableFuture<>();
        audience.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1))
                .required(true)
                .replace(true)
                .prompt(messages.get("pack.prompt"))
                .callback((id, status, a) -> {
                    if (!status.intermediate()) {
                        result.complete(status);
                    }
                }));
        try {
            return result.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return null;
        }
    }

    /**
     * Also serves {@code text()} (UTF-8 plain text) at {@code path} on the pack's HTTP port, e.g. the
     * queue status the survival server reads. {@code text} is called on an HTTP thread.
     */
    public void serveText(String path, Supplier<String> text) {
        server.createContext(path, exchange -> {
            try (exchange) {
                if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().getPath().equals(path)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] body = text.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
                if (body.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            }
        });
    }

    public int size() {
        return zip.length;
    }

    public void stop() {
        server.stop(0);
    }
}
