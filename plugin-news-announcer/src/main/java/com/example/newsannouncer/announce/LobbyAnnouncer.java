package com.example.newsannouncer.announce;

import com.example.newsannouncer.PluginUpdate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * Relaie les nouveautés détectées vers le serveur "lobby" via un canal moderne dédié
 * ("pluginnews:feed"), relayé entre serveurs par le plugin Velocity plugin-news-proxy.
 *
 * On n'utilise volontairement PAS le canal legacy "BungeeCord"/"Forward" : celui-ci a un
 * bug connu sur Velocity qui empêche le relai correct vers un sous-canal personnalisé
 * (https://github.com/PaperMC/Velocity/issues/1312), ce qui ferait que les messages
 * n'arriveraient jamais au lobby, sans aucune erreur visible.
 *
 * Format du message envoyé : [UTF nom-du-serveur-cible][octets bruts du JSON].
 * Le plugin-news-proxy lit le nom du serveur cible, puis relaie le reste tel quel sur le
 * même canal vers ce serveur. Le plugin PluginNewsLobby installé sur le lobby reçoit donc
 * directement les octets JSON, sans encapsulation supplémentaire à décoder.
 */
public class LobbyAnnouncer {

    private static final String CHANNEL = "pluginnews:feed";

    private final Plugin plugin;
    private final String targetServer;

    public LobbyAnnouncer(Plugin plugin, String targetServer) {
        this.plugin = plugin;
        this.targetServer = targetServer;
    }

    public void send(String worldName, List<PluginUpdate> updates) {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            plugin.getLogger().info("[PluginNewsAnnouncer] Aucun joueur en ligne, envoi au lobby différé.");
            return;
        }
        Player carrier = online.iterator().next();

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("world", worldName);
            payload.addProperty("timestamp", System.currentTimeMillis());

            JsonArray array = new JsonArray();
            for (PluginUpdate u : updates) {
                JsonObject entry = new JsonObject();
                entry.addProperty("plugin", u.pluginName);
                entry.addProperty("type", u.type.name());
                entry.addProperty("oldVersion", u.oldVersion);
                entry.addProperty("newVersion", u.newVersion);
                entry.addProperty("changelog", u.changelog);
                array.add(entry);
            }
            payload.add("updates", array);

            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            out.writeUTF(targetServer);
            out.write(jsonBytes);

            carrier.sendPluginMessage(plugin, CHANNEL, byteArray.toByteArray());

            plugin.getLogger().info("[PluginNewsAnnouncer] " + updates.size()
                    + " nouveauté(s) envoyée(s) vers le lobby (\"" + targetServer + "\").");
        } catch (Exception e) {
            plugin.getLogger().warning("[PluginNewsAnnouncer] Échec relai lobby : " + e.getMessage());
        }
    }
}
