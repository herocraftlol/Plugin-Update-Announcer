package com.example.newsannouncer;

import com.example.newsannouncer.announce.DiscordAnnouncer;
import com.example.newsannouncer.announce.LobbyAnnouncer;
import com.example.newsannouncer.announce.WebsiteAnnouncer;
import com.example.newsannouncer.changelog.ChangelogFetcher;
import com.example.newsannouncer.changelog.GithubChangelogFetcher;
import com.example.newsannouncer.changelog.GithubRepoResolver;
import com.example.newsannouncer.changelog.ManualChangelogFetcher;
import com.example.newsannouncer.changelog.SpigotChangelogFetcher;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public class PluginNewsAnnouncer extends JavaPlugin implements Listener {

    private GithubRepoResolver repoResolver;
    private List<PluginUpdate> pendingLobbyUpdates;
    private String pendingLobbyWorldName;
    private LobbyAnnouncer lobbyAnnouncer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);

        String username = getConfig().getString("github.username", "");
        if (!username.isBlank()) {
            repoResolver = new GithubRepoResolver(username, getConfig().getString("github.token", ""));
        }

        getServer().getScheduler().runTaskAsynchronously(this, this::runDetectionCycle);
    }

    private void runDetectionCycle() {
        try {
            File pluginsFolder = new File("plugins");
            PluginScanner scanner = new PluginScanner();
            Map<String, String> current = scanner.scan(pluginsFolder);

            SnapshotStore store = new SnapshotStore(getDataFolder());
            Map<String, String> previous = store.load();

            UpdateDetector detector = new UpdateDetector();
            List<PluginUpdate> updates = detector.diff(previous, current);

            if (updates.isEmpty()) {
                getLogger().info("[PluginNewsAnnouncer] Aucun changement de plugin détecté.");
                store.save(current);
                return;
            }

            for (PluginUpdate update : updates) {
                if (update.type == PluginUpdate.Type.UPDATED) {
                    update.changelog = fetchChangelogSafely(update);
                }
            }

            announceAll(updates);
            store.save(current);

        } catch (Exception e) {
            getLogger().warning("[PluginNewsAnnouncer] Erreur pendant le cycle de détection : " + e.getMessage());
        }
    }

    /**
     * Ordre de résolution du changelog :
     *  1) Override manuel dans config.yml (section "plugins:") si présent
     *  2) Sinon, matching automatique via la liste des repos GitHub de l'utilisateur configuré
     *  3) Sinon, pas de changelog détaillé (le plugin est quand même annoncé, juste sans détail)
     */
    private String fetchChangelogSafely(PluginUpdate update) {
        ConfigurationSection override = getConfig().getConfigurationSection("plugins." + update.pluginName);

        try {
            if (override != null) {
                ChangelogFetcher fetcher = buildFetcherFromOverride(override);
                if (fetcher != null) {
                    return fetcher.fetch(update.pluginName, update.oldVersion, update.newVersion);
                }
            }

            if (repoResolver != null) {
                String repo = repoResolver.resolveRepo(update.pluginName);
                if (repo != null) {
                    String tagPrefix = getConfig().getString("github.default-tag-prefix", "v");
                    ChangelogFetcher fetcher = new GithubChangelogFetcher(repo, tagPrefix, getConfig().getString("github.token", ""));
                    String changelog = fetcher.fetch(update.pluginName, update.oldVersion, update.newVersion);
                    if (changelog != null) return changelog;

                    // Retente avec un tag sans préfixe si la première tentative échoue
                    ChangelogFetcher fallback = new GithubChangelogFetcher(repo, "", getConfig().getString("github.token", ""));
                    return fallback.fetch(update.pluginName, update.oldVersion, update.newVersion);
                } else {
                    getLogger().info("[PluginNewsAnnouncer] Aucun repo GitHub trouvé pour \"" + update.pluginName
                            + "\" chez " + getConfig().getString("github.username") + " — annonce sans changelog détaillé.");
                }
            }
        } catch (Exception e) {
            getLogger().warning("[PluginNewsAnnouncer] Échec récupération changelog pour "
                    + update.pluginName + " : " + e.getMessage());
        }
        return null;
    }

    private ChangelogFetcher buildFetcherFromOverride(ConfigurationSection section) {
        String source = section.getString("source", "none");
        return switch (source) {
            case "github" -> new GithubChangelogFetcher(
                    section.getString("repo"),
                    section.getString("tag-prefix", ""),
                    getConfig().getString("github.token", "")
            );
            case "spigot" -> new SpigotChangelogFetcher(section.getInt("resource-id"));
            case "manual" -> new ManualChangelogFetcher(new File(section.getString("changelog-file")));
            default -> null;
        };
    }

    private void announceAll(List<PluginUpdate> updates) {
        String serverName = getConfig().getString("server-name", "Serveur");
        MessageFormatter formatter = new MessageFormatter();

        if (getConfig().getBoolean("discord.enabled", false)) {
            String webhook = getConfig().getString("discord.webhook-url");
            new DiscordAnnouncer(webhook).send(formatter.formatForDiscord(serverName, updates));
        }

        if (getConfig().getBoolean("website.enabled", false)) {
            String apiUrl = getConfig().getString("website.api-url");
            String apiKey = getConfig().getString("website.api-key");
            new WebsiteAnnouncer(apiUrl, apiKey).send(serverName, updates);
        }

        if (getConfig().getBoolean("lobby.enabled", false)) {
            lobbyAnnouncer = new LobbyAnnouncer(this, getConfig().getString("lobby.target-server", "lobby"));

            if (!getServer().getOnlinePlayers().isEmpty()) {
                lobbyAnnouncer.send(serverName, updates);
            } else {
                pendingLobbyUpdates = updates;
                pendingLobbyWorldName = serverName;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (pendingLobbyUpdates != null && lobbyAnnouncer != null) {
            List<PluginUpdate> updatesToSend = pendingLobbyUpdates;
            String worldName = pendingLobbyWorldName;
            pendingLobbyUpdates = null;
            pendingLobbyWorldName = null;

            getServer().getScheduler().runTaskLater(this, () ->
                    lobbyAnnouncer.send(worldName, updatesToSend), 20L);
        }
    }
}
