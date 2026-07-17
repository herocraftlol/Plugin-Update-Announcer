package com.example.newsannouncer.announce;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Collection;

/**
 * Relaie une annonce au serveur "lobby" via le canal standard "BungeeCord" (sous-canal Forward).
 * Le plugin doit être enregistré comme canal sortant "BungeeCord" dans onEnable().
 *
 * Un plugin côté BungeeCord (voir bungee-listener/) doit écouter le sous-canal
 * "pluginnews:announce" et faire le broadcast réel aux joueurs du lobby.
 */
public class LobbyAnnouncer {

    private final Plugin plugin;
    private final String targetServer;
    private final String displayType; // CHAT, TITLE, ACTIONBAR

    public LobbyAnnouncer(Plugin plugin, String targetServer, String displayType) {
        this.plugin = plugin;
        this.targetServer = targetServer;
        this.displayType = displayType;
    }

    public void send(String message) {
        // Le plugin message doit être envoyé "depuis" un joueur connecté, c'est une contrainte
        // du protocole BungeeCord. On prend le premier joueur en ligne, peu importe lequel.
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            plugin.getLogger().info("[PluginNewsAnnouncer] Aucun joueur en ligne, annonce lobby ignorée.");
            return;
        }
        Player carrier = online.iterator().next();

        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);

            out.writeUTF("Forward");
            out.writeUTF(targetServer);
            out.writeUTF("pluginnews:announce");

            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF(displayType);
            msgOut.writeUTF(message);

            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());

            carrier.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("[PluginNewsAnnouncer] Échec relai lobby : " + e.getMessage());
        }
    }
}
