package be.dbsdw.minigames.core.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

/**
 * Simple Voice Chat groups for matches: hidden, password-protected, isolated groups that only the
 * core puts players in. Loaded only when the voicechat plugin is installed.
 */
public final class VoicechatBridge implements MatchVoice, VoicechatPlugin {
    private record Wanted(String key, String name) {}

    private final Plugin plugin;
    private volatile VoicechatServerApi api;
    /** The group each match player belongs in. Read from voice chat threads too. */
    private final Map<UUID, Wanted> wanted = new ConcurrentHashMap<>();
    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private final Set<UUID> ownGroupIds = ConcurrentHashMap.newKeySet();

    private VoicechatBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registers with Simple Voice Chat; {@link MatchVoice#NONE} if its service is missing. */
    public static MatchVoice create(Plugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().warning("[voice] voicechat is installed but offers no API service; no team voice groups");
            return MatchVoice.NONE;
        }
        VoicechatBridge bridge = new VoicechatBridge(plugin);
        service.registerPlugin(bridge);
        plugin.getLogger().info("[voice] Simple Voice Chat found: matches get voice groups");
        return bridge;
    }

    @Override
    public String getPluginId() {
        return "dbsdw-minigames";
    }

    @Override
    public void initialize(VoicechatApi given) {
        // On a server this is the server API; it also covers a voice server that started before us.
        if (given instanceof VoicechatServerApi server) {
            api = server;
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> api = event.getVoicechat());
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            api = null;
            groups.clear();
            ownGroupIds.clear();
        });
        // A player who connects their voice chat late (or again) lands in their match group.
        registration.registerEvent(PlayerConnectedEvent.class, event -> {
            UUID id = event.getConnection().getPlayer().getUuid();
            if (wanted.containsKey(id) && plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> apply(id));
            }
        });
        registration.registerEvent(JoinGroupEvent.class, this::onJoin);
    }

    /** Match players stay in their match group; nobody else joins a match group. */
    private void onJoin(JoinGroupEvent event) {
        VoicechatConnection connection = event.getConnection();
        Group target = event.getGroup();
        if (connection == null || target == null) {
            return;
        }
        Wanted want = wanted.get(connection.getPlayer().getUuid());
        Group own = want == null ? null : groups.get(want.key());
        boolean allowed = want == null ? !ownGroupIds.contains(target.getId())
                : own != null && own.getId().equals(target.getId());
        if (!allowed) {
            event.cancel();
        }
    }

    @Override
    public void put(UUID player, String groupKey, String groupName) {
        wanted.put(player, new Wanted(groupKey, VoiceRule.groupName(groupName)));
        apply(player);
    }

    @Override
    public void clear(UUID player) {
        if (wanted.remove(player) != null) {
            apply(player);
        }
    }

    @Override
    public void removeGroups(String matchKey) {
        VoicechatServerApi a = api;
        String prefix = matchKey + ":";
        for (String key : Set.copyOf(groups.keySet())) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            Group group = groups.remove(key);
            if (group == null) {
                continue;
            }
            ownGroupIds.remove(group.getId());
            if (a != null) {
                try {
                    a.removeGroup(group.getId());
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "[voice] could not remove group " + key, e);
                }
            }
        }
    }

    @Override
    public boolean connected(UUID player) {
        VoicechatServerApi a = api;
        if (a == null) {
            return false;
        }
        VoicechatConnection connection = a.getConnectionOf(player);
        return connection != null && connection.isConnected() && !connection.isDisabled();
    }

    /** Moves the player's voice chat connection to the group they belong in (or out of ours). */
    private void apply(UUID player) {
        VoicechatServerApi a = api;
        if (a == null) {
            return;
        }
        try {
            VoicechatConnection connection = a.getConnectionOf(player);
            if (connection == null) {
                return;
            }
            Group current = connection.getGroup();
            Wanted want = wanted.get(player);
            if (want == null) {
                if (current != null && ownGroupIds.contains(current.getId())) {
                    connection.setGroup(null);
                }
                return;
            }
            Group group = groups.computeIfAbsent(want.key(), key -> {
                Group made = a.groupBuilder()
                        .setName(want.name())
                        .setPassword(UUID.randomUUID().toString())
                        .setHidden(true)
                        .setPersistent(true)
                        .setType(Group.Type.ISOLATED)
                        .build();
                ownGroupIds.add(made.getId());
                return made;
            });
            if (current == null || !current.getId().equals(group.getId())) {
                connection.setGroup(group);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "[voice] could not set the voice group of " + player, e);
        }
    }
}
