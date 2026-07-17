package com.example.newsannouncer;

import com.example.newsannouncer.announce.DiscordAnnouncer;
import com.example.newsannouncer.announce.LobbyAnnouncer;
import com.example.newsannouncer.announce.WebsiteAnnouncer;
import com.example.newsannouncer.changelog.ChangelogFetcher;
import com.example.newsannouncer.changelog.GithubChangelogFetcher;
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

    private List<PluginUpdate> pendingLobbyUpdates = null;
    private LobbyAnnouncer lobbyAnnouncer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);

        // Tout le travail réseau (GitHub/Spigot) se fait en asynchrone pour ne jamais
        // bloquer le démarrage du serveur.
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

            // Enrichir chaque update avec son changelog si une source est configurée
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

    private String fetchChangelogSafely(PluginUpdate update) {
        ConfigurationSection section = getConfig().getConfigurationSection("plugins." + update.pluginName);
        if (section == null) return null; // pas configuré, on annonce sans détail

        String source = section.getString("source", "none");
        try {
            ChangelogFetcher fetcher = switch (source) {
                case "github" -> new GithubChangelogFetcher(
                        section.getString("repo"),
                        section.getString("tag-prefix", ""),
                        getConfig().getString("github.token", "")
                );
                case "spigot" -> new SpigotChangelogFetcher(section.getInt("resource-id"));
                case "manual" -> new ManualChangelogFetcher(new File(section.getString("changelog-file")));
                default -> null;
            };
            if (fetcher == null) return null;
            return fetcher.fetch(update.pluginName, update.oldVersion, update.newVersion);
        } catch (Exception e) {
            getLogger().warning("[PluginNewsAnnouncer] Échec récupération changelog pour "
                    + update.pluginName + " : " + e.getMessage());
            return null;
        }
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
            lobbyAnnouncer = new LobbyAnnouncer(
                    this,
                    getConfig().getString("lobby.target-server", "lobby"),
                    getConfig().getString("lobby.display-type", "CHAT")
            );
            String lobbyMessage = formatter.formatForLobby(updates);

            // Si des joueurs sont déjà en ligne (redémarrage rapide / reload), on envoie tout de suite.
            if (!getServer().getOnlinePlayers().isEmpty()) {
                lobbyAnnouncer.send(lobbyMessage);
            } else {
                // Sinon on attend la première connexion (voir onPlayerJoin ci-dessous).
                pendingLobbyUpdates = updates;
                this.pendingLobbyMessage = lobbyMessage;
            }
        }
    }

    private String pendingLobbyMessage;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (pendingLobbyMessage != null && lobbyAnnouncer != null) {
            // Léger délai pour laisser le temps à la connexion Bungee de bien s'établir
            getServer().getScheduler().runTaskLater(this, () -> {
                lobbyAnnouncer.send(pendingLobbyMessage);
                pendingLobbyMessage = null;
                pendingLobbyUpdates = null;
            }, 20L); // 1 seconde
        }
    }
}
