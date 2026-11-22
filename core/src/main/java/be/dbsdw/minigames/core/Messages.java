package be.dbsdw.minigames.core;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player messages from messages.yml, with the jar's copy as fallback for missing keys.
 *
 * <p>The bundled file carries a {@code version}. When the server's copy is older, it is renamed to
 * {@code messages.yml.v<old>.bak} and replaced by the new defaults, so rewritten texts reach servers
 * that already have the file (missing keys alone would only add new texts, never change old ones).
 */
public final class Messages {
    private final YamlConfiguration yaml;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public static Messages load(JavaPlugin plugin) {
        YamlConfiguration bundled;
        try (InputStream in = plugin.getResource("messages.yml");
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the bundled messages.yml", e);
        }
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (file.exists()) {
            int current = YamlConfiguration.loadConfiguration(file).getInt("version", 0);
            int latest = bundled.getInt("version", 0);
            if (current < latest) {
                File backup = new File(plugin.getDataFolder(), "messages.yml.v" + current + ".bak");
                try {
                    Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot back up the old messages.yml", e);
                }
                plugin.getLogger().info("messages.yml v" + current + " replaced by v" + latest
                        + "; the old file is " + backup.getName());
            }
        }
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.setDefaults(bundled);
        return new Messages(yaml);
    }

    public Component get(String key, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(yaml.getString(key, key), resolvers);
    }

    /** The raw MiniMessage text of a key (for APIs that take a string). */
    public String raw(String key) {
        return yaml.getString(key, key);
    }
}
