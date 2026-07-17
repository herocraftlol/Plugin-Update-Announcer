package com.example.newsannouncer.bungee;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.event.PluginMessageEvent;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class PluginNewsListener extends Plugin implements Listener {

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);
        // Nécessaire pour que Bukkit/Paper puisse envoyer sur ce canal custom via "Forward"
        getProxy().registerChannel("pluginnews:announce");
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals("BungeeCord")) return;

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
        try {
            String subChannel = in.readUTF();
            if (!subChannel.equals("pluginnews:announce")) return;

            short len = in.readShort();
            byte[] payload = new byte[len];
            in.readFully(payload);

            DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(payload));
            String displayType = msgIn.readUTF(); // CHAT, TITLE, ACTIONBAR
            String message = msgIn.readUTF();

            broadcastToLobby(displayType, message);
        } catch (Exception e) {
            getLogger().warning("Erreur lecture plugin message pluginnews:announce : " + e.getMessage());
        }
    }

    private void broadcastToLobby(String displayType, String rawMessage) {
        ServerInfo lobby = ProxyServer.getInstance().getServerInfo("lobby");
        if (lobby == null) return;

        TextComponent component = new TextComponent(rawMessage.replace('§', '\u00A7'));

        for (ProxiedPlayer player : lobby.getPlayers()) {
            switch (displayType) {
                case "TITLE" -> {
                    net.md_5.bungee.api.Title title = ProxyServer.getInstance().createTitle();
                    title.title(component);
                    title.fadeIn(10).stay(60).fadeOut(10);
                    player.sendTitle(title);
                }
                case "ACTIONBAR" -> player.sendMessage(ChatMessageType.ACTION_BAR, component);
                default -> player.sendMessage(ChatMessageType.CHAT, component);
            }
        }
    }
}
