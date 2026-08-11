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
    private RecentReleaseStore recentReleaseStore;
    private List<PluginUpdate> pendingLobbyUpdates;
    private String pendingLobbyWorldName;
    private LobbyAnnouncer lobbyAnnouncer;

    // Empêche deux cycles de détection de tourner en même temps (scan périodique qui
    // chevaucherait le scan de démarrage, par ex.)
    private final java.util.concurrent.atomic.AtomicBoolean scanInProgress =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // On utilise un canal moderne dédié plutôt que le "BungeeCord" legacy : voir la
        // javadoc de LobbyAnnouncer pour l'explication du bug Velocity contourné.
        getServer().getMessenger().registerOutgoingPluginChannel(this, "pluginnews:feed");
        getServer().getPluginManager().registerEvents(this, this);

        String username = getConfig().getString("github.username", "");
        if (!username.isBlank()) {
            repoResolver = new GithubRepoResolver(username, getConfig().getString("github.token", ""));
        }
        recentReleaseStore = new RecentReleaseStore(getDataFolder());

        // Premier scan immédiat au démarrage
        getServer().getScheduler().runTaskAsynchronously(this, this::runDetectionCycle);

        // Puis, pour un suivi "temps réel" sans avoir à redémarrer le serveur : on
        // relance périodiquement le même cycle (scan des jars + vérification des
        // dernières releases GitHub) selon l'intervalle configuré.
        int intervalMinutes = getConfig().getInt("scan-interval-minutes", 15);
        if (intervalMinutes > 0) {
            long periodTicks = intervalMinutes * 60L * 20L; // minutes -> ticks (20 ticks/s)
            getServer().getScheduler().runTaskTimerAsynchronously(
                    this, this::runDetectionCycle, periodTicks, periodTicks);
            getLogger().info("[PluginNewsAnnouncer] Vérification périodique activée toutes les "
                    + intervalMinutes + " minute(s).");
        } else {
            getLogger().info("[PluginNewsAnnouncer] Vérification périodique désactivée (scan au démarrage uniquement).");
        }
    }

    private void runDetectionCycle() {
        if (!scanInProgress.compareAndSet(false, true)) {
            getLogger().info("[PluginNewsAnnouncer] Cycle de détection déjà en cours, on saute ce passage.");
            return;
        }
        try {
            File pluginsFolder = new File("plugins");
            PluginScanner scanner = new PluginScanner();
            Map<String, String> current = scanner.scan(pluginsFolder);

            SnapshotStore store = new SnapshotStore(getDataFolder());
            Map<String, String> previous = store.load();

            UpdateDetector detector = new UpdateDetector();
            List<PluginUpdate> updates = new java.util.ArrayList<>(detector.diff(previous, current));

            for (PluginUpdate update : updates) {
                if (update.type == PluginUpdate.Type.UPDATED || update.type == PluginUpdate.Type.ADDED) {
                    update.changelog = fetchChangelogSafely(update);
                }
            }

            // Vérifie aussi les plugins déjà installés dont la version locale n'a pas changé :
            // leur release GitHub correspondante a peut-être été publiée récemment.
            if (getConfig().getBoolean("github.recent-release-check.enabled", true)) {
                java.util.Set<String> alreadyHandled = new java.util.HashSet<>();
                for (PluginUpdate u : updates) alreadyHandled.add(u.pluginName);
                updates.addAll(checkRecentReleasesForUnchangedPlugins(current, alreadyHandled));
            }

            store.save(current);

            if (updates.isEmpty()) {
                getLogger().info("[PluginNewsAnnouncer] Aucun changement de plugin détecté.");
                return;
            }

            announceAll(updates);

        } catch (Exception e) {
            getLogger().warning("[PluginNewsAnnouncer] Erreur pendant le cycle de détection : " + e.getMessage());
        } finally {
            scanInProgress.set(false);
        }
    }

    /**
     * Pour les plugins déjà installés dont la version LOCALE n'a pas changé entre deux
     * scans (donc invisibles pour UpdateDetector), vérifie si la release GitHub
     * correspondant à leur version actuelle a été publiée récemment. Utile quand un plugin
     * est déployé une seule fois avec une version qui, elle, est neuve sur GitHub : sans
     * cette vérification, cette nouveauté ne serait jamais détectée (aucun changement local
     * ne se produira jamais tant que le jar n'est pas remplacé).
     *
     * Chaque version n'est signalée qu'UNE SEULE FOIS par plugin, grâce à RecentReleaseStore
     * (sinon ce serait ré-annoncé à chaque cycle de scan tant qu'on reste dans la fenêtre).
     */
    private List<PluginUpdate> checkRecentReleasesForUnchangedPlugins(Map<String, String> current, java.util.Set<String> alreadyHandled) {
        List<PluginUpdate> result = new java.util.ArrayList<>();
        if (repoResolver == null) return result;
        if (!getConfig().getBoolean("github.recent-release-check.enabled", true)) return result;

        int windowDays = getConfig().getInt("github.recent-release-check.window-days", 7);
        String tagPrefix = getConfig().getString("github.default-tag-prefix", "v");
        String token = getConfig().getString("github.token", "");
        long cutoff = System.currentTimeMillis() - windowDays * 24L * 60L * 60L * 1000L;

        for (var entry : current.entrySet()) {
            String pluginName = entry.getKey();
            String version = entry.getValue();
            if (alreadyHandled.contains(pluginName)) continue;
            if (recentReleaseStore.alreadyAnnounced(pluginName, version)) continue;

            try {
                String repo = repoResolver.resolveRepo(pluginName);
                if (repo == null) continue;

                GithubChangelogFetcher fetcher = new GithubChangelogFetcher(repo, tagPrefix, token, windowDays);
                Long publishedAt = fetcher.getPublishedAtForVersion(version);
                if (publishedAt == null) {
                    fetcher = new GithubChangelogFetcher(repo, "", token, windowDays);
                    publishedAt = fetcher.getPublishedAtForVersion(version);
                }
                if (publishedAt == null) continue;
                if (publishedAt < cutoff) continue;

                PluginUpdate synthetic = new PluginUpdate(pluginName, PluginUpdate.Type.RECENT, null, version);
                synthetic.changelog = fetcher.fetch(pluginName, null, version);
                result.add(synthetic);

                recentReleaseStore.markAnnounced(pluginName, version);
            } catch (Exception e) {
                getLogger().warning("[PluginNewsAnnouncer] Erreur vérification release récente pour "
                        + pluginName + " : " + e.getMessage());
            }
        }
        return result;
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
                    int windowDays = getConfig().getInt("github.changelog-window-days", 7);
                    ChangelogFetcher fetcher = new GithubChangelogFetcher(repo, tagPrefix, getConfig().getString("github.token", ""), windowDays);
                    String changelog = fetcher.fetch(update.pluginName, update.oldVersion, update.newVersion);
                    if (changelog != null) return changelog;

                    // Retente avec un tag sans préfixe si la première tentative échoue
                    ChangelogFetcher fallback = new GithubChangelogFetcher(repo, "", getConfig().getString("github.token", ""), windowDays);
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
                    getConfig().getString("github.token", ""),
                    getConfig().getInt("github.changelog-window-days", 7)
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
