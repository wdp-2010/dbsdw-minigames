package be.dbsdw.minigames.bridge;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Player messages from messages.yml, with the jar's copy as fallback for missing keys. */
final class Messages {
    private final YamlConfiguration yaml;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml");
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the bundled messages.yml", e);
        }
        return new Messages(yaml);
    }

    Component get(String key, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(yaml.getString(key, key), resolvers);
    }
}
