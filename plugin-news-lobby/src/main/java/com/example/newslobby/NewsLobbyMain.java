package com.example.newslobby;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NewsLobbyMain extends JavaPlugin implements Listener, PluginMessageListener {

    private static final DateTimeFormatter DATE_CMD_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private NewsFeedStore feedStore;
    private PlayerLastSeenStore lastSeenStore;
    private BookBuilder bookBuilder;
    private NewsHttpApi httpApi;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        feedStore = new NewsFeedStore(getDataFolder(), getConfig().getInt("retention-days", 60));
        lastSeenStore = new PlayerLastSeenStore(getDataFolder());
        bookBuilder = new BookBuilder();

        getServer().getMessenger().registerIncomingPluginChannel(this, "pluginnews:feed", this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("nouveautes").setExecutor(this::onNouveautesCommand);

        if (getConfig().getBoolean("http-api.enabled", false)) {
            httpApi = new NewsHttpApi(
                    feedStore,
                    getConfig().getInt("default-window-days", 7),
                    getConfig().getString("http-api.api-key", "")
            );
            httpApi.start(getConfig().getInt("http-api.port", 8085));
        }
    }

    @Override
    public void onDisable() {
        if (httpApi != null) httpApi.stop();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("pluginnews:feed")) return;

        try {
            // Le plugin Velocity plugin-news-proxy relaie ici directement les octets JSON
            // bruts (il a déjà retiré le nom du serveur cible utilisé pour le routage).
            String json = new String(message, StandardCharsets.UTF_8);

            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            String world = payload.get("world").getAsString();
            long timestamp = payload.get("timestamp").getAsLong();
            JsonArray updates = payload.getAsJsonArray("updates");

            List<NewsEntry> entries = new ArrayList<>();
            for (var el : updates) {
                JsonObject u = el.getAsJsonObject();
                entries.add(new NewsEntry(
                        world,
                        u.get("plugin").getAsString(),
                        u.get("type").getAsString(),
                        getNullableString(u, "oldVersion"),
                        getNullableString(u, "newVersion"),
                        getNullableString(u, "changelog"),
                        timestamp
                ));
            }

            feedStore.append(entries);
            pingOnlinePlayers(world);

        } catch (Exception e) {
            getLogger().warning("[PluginNewsLobby] Erreur de lecture du flux reçu : " + e.getMessage());
        }
    }

    private String getNullableString(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }

    /** Ping court dans le chat pour les joueurs déjà connectés, sans attendre leur prochaine connexion. */
    private void pingOnlinePlayers(String world) {
        if (!getConfig().getBoolean("chat-ping.enabled", true)) return;

        String msg = ChatColor.AQUA + "[Nouveautés] " + ChatColor.WHITE
                + "De nouvelles mises à jour sont disponibles sur " + ChatColor.YELLOW + world
                + ChatColor.WHITE + " ! Tape " + ChatColor.GOLD + "/nouveautes" + ChatColor.WHITE + " pour les voir.";

        getServer().getScheduler().runTask(this, () ->
                getServer().getOnlinePlayers().forEach(p -> p.sendMessage(msg)));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        getServer().getScheduler().runTaskLater(this, () -> {
            boolean autoOpenDefault = getConfig().getBoolean("auto-open.default", true);
            boolean autoOpenEnabled = lastSeenStore.isAutoOpenEnabled(player.getUniqueId(), autoOpenDefault);

            long now = System.currentTimeMillis();

            // Fenêtre MINIMALE garantie : au moins les N derniers jours (default-window-days),
            // même si le joueur s'est connecté récemment. On ne réduit jamais cette fenêtre,
            // on ne fait que l'élargir si sa dernière visite date de plus longtemps.
            long minWindowCutoff = now - TimeUnit.DAYS.toMillis(getConfig().getInt("default-window-days", 7));
            long firstJoinCutoff = now - TimeUnit.DAYS.toMillis(getConfig().getInt("first-join-window-days", 7));

            long lastSeen = lastSeenStore.getLastSeen(player.getUniqueId());
            long sinceLastVisitCutoff = (lastSeen == -1) ? firstJoinCutoff : lastSeen;

            // On prend la fenêtre la PLUS ANCIENNE des deux, pour ne jamais montrer moins
            // que default-window-days, tout en remontant plus loin si la dernière visite
            // du joueur est plus ancienne que ça.
            long effectiveCutoff = Math.min(sinceLastVisitCutoff, minWindowCutoff);

            lastSeenStore.setLastSeen(player.getUniqueId(), now);

            if (!autoOpenEnabled) {
                return; // le joueur a désactivé l'ouverture automatique via /nouveautes auto off
            }

            long minIntervalMillis = TimeUnit.HOURS.toMillis(getConfig().getInt("auto-open.min-interval-hours", 6));
            long lastAutoOpen = lastSeenStore.getLastAutoOpen(player.getUniqueId());
            if (lastAutoOpen != -1 && (now - lastAutoOpen) < minIntervalMillis) {
                return; // déjà montré récemment, on évite de spammer le joueur
            }

            List<NewsEntry> newEntries = feedStore.getSince(effectiveCutoff);

            if (!newEntries.isEmpty()) {
                player.openBook(bookBuilder.build(newEntries,
                        getConfig().getString("book-title", "Nouveautés"),
                        getConfig().getString("book-author", "Serveur")));
                lastSeenStore.setLastAutoOpen(player.getUniqueId(), now);
            }

        }, 40L); // 2 secondes après connexion, pour laisser le client bien charger
    }

    private boolean onNouveautesCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("auto")) {
            return handleAutoSubcommand(player, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("date")) {
            return handleDateSubcommand(player, args);
        }

        long cutoff = System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(getConfig().getInt("review-window-days", 30));

        List<NewsEntry> entries = feedStore.getSince(cutoff);

        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Aucune nouveauté récente à afficher.");
            return true;
        }

        player.openBook(bookBuilder.build(entries,
                getConfig().getString("book-title", "Nouveautés"),
                getConfig().getString("book-author", "Serveur")));
        return true;
    }

    /** /nouveautes auto on|off — active/désactive l'ouverture automatique du livre à la connexion. */
    private boolean handleAutoSubcommand(Player player, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            boolean current = lastSeenStore.isAutoOpenEnabled(
                    player.getUniqueId(), getConfig().getBoolean("auto-open.default", true));
            player.sendMessage(ChatColor.GRAY + "Affichage automatique actuel : "
                    + (current ? ChatColor.GREEN + "activé" : ChatColor.RED + "désactivé"));
            player.sendMessage(ChatColor.GRAY + "Utilise " + ChatColor.GOLD + "/nouveautes auto on"
                    + ChatColor.GRAY + " ou " + ChatColor.GOLD + "/nouveautes auto off" + ChatColor.GRAY + ".");
            return true;
        }

        boolean enable = args[1].equalsIgnoreCase("on");
        lastSeenStore.setAutoOpenEnabled(player.getUniqueId(), enable);
        player.sendMessage(ChatColor.AQUA + "[Nouveautés] " + ChatColor.WHITE
                + "Affichage automatique du livre à la connexion : "
                + (enable ? ChatColor.GREEN + "activé" : ChatColor.RED + "désactivé") + ChatColor.WHITE + ".");
        return true;
    }

    /** /nouveautes date jj/mm/aaaa — consulte le journal des nouveautés d'un jour précis. */
    private boolean handleDateSubcommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.GRAY + "Utilise " + ChatColor.GOLD + "/nouveautes date jj/mm/aaaa" + ChatColor.GRAY + ".");
            return true;
        }

        try {
            LocalDate date = LocalDate.parse(args[1], DATE_CMD_FORMAT);
            List<NewsEntry> entries = feedStore.getForDate(date);

            if (entries.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Aucune nouveauté enregistrée pour le " + args[1] + ".");
                return true;
            }

            player.openBook(bookBuilder.build(entries,
                    "Nouveautés du " + args[1],
                    getConfig().getString("book-author", "Serveur")));
        } catch (DateTimeParseException e) {
            player.sendMessage(ChatColor.RED + "Date invalide, format attendu : jj/mm/aaaa (ex: 05/08/2026).");
        }
        return true;
    }
}
