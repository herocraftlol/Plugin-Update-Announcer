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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NewsLobbyMain extends JavaPlugin implements Listener, PluginMessageListener {

    private NewsFeedStore feedStore;
    private PlayerLastSeenStore lastSeenStore;
    private BookBuilder bookBuilder;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        feedStore = new NewsFeedStore(getDataFolder(), getConfig().getInt("retention-days", 60));
        lastSeenStore = new PlayerLastSeenStore(getDataFolder());
        bookBuilder = new BookBuilder();

        getServer().getMessenger().registerIncomingPluginChannel(this, "pluginnews:feed", this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("nouveautes").setExecutor(this::onNouveautesCommand);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("pluginnews:feed")) return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            short len = in.readShort();
            byte[] jsonBytes = new byte[len];
            in.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

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
            long lastSeen = lastSeenStore.getLastSeen(player.getUniqueId());
            long cutoff = (lastSeen == -1)
                    ? System.currentTimeMillis() - TimeUnit.DAYS.toMillis(getConfig().getInt("first-join-window-days", 7))
                    : lastSeen;

            List<NewsEntry> newEntries = feedStore.loadAll().stream()
                    .filter(e -> e.timestamp > cutoff)
                    .toList();

            if (!newEntries.isEmpty()) {
                player.openBook(bookBuilder.build(newEntries, getConfig().getString("book-title", "Nouveautés")));
            }
            lastSeenStore.setLastSeen(player.getUniqueId(), System.currentTimeMillis());

        }, 40L); // 2 secondes après connexion, pour laisser le client bien charger
    }

    private boolean onNouveautesCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Cette commande est réservée aux joueurs.");
            return true;
        }

        long cutoff = System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(getConfig().getInt("review-window-days", 30));

        List<NewsEntry> entries = feedStore.loadAll().stream()
                .filter(e -> e.timestamp > cutoff)
                .toList();

        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Aucune nouveauté récente à afficher.");
            return true;
        }

        player.openBook(bookBuilder.build(entries, getConfig().getString("book-title", "Nouveautés")));
        return true;
    }
}
