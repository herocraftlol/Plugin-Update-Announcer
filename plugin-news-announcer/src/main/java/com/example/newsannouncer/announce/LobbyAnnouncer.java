package com.example.newsannouncer.announce;

import com.example.newsannouncer.PluginUpdate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Collection;
import java.util.List;

/**
 * Relaie les nouveautés détectées vers le serveur "lobby" via le canal BungeeCord natif
 * (sous-canal "Forward", qui ne nécessite AUCUN plugin custom côté proxy : BungeeCord
 * relaie nativement le contenu au canal "pluginnews:feed" enregistré côté serveur lobby).
 *
 * Le plugin PluginNewsLobby installé sur le lobby reçoit ce JSON, l'ajoute à l'historique
 * agrégé de tous les mondes, et gère l'affichage (livre + ping de chat) par joueur.
 */
public class LobbyAnnouncer {

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

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);

            out.writeUTF("Forward");
            out.writeUTF(targetServer);
            out.writeUTF("pluginnews:feed");

            byte[] jsonBytes = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeShort(jsonBytes.length);
            msgOut.write(jsonBytes);

            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());

            carrier.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("[PluginNewsAnnouncer] Échec relai lobby : " + e.getMessage());
        }
    }
}
