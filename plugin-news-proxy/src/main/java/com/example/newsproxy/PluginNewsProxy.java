package com.example.newsproxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.util.Optional;

/**
 * Plugin Velocity minimal dont le seul rôle est de relayer les messages du canal
 * "pluginnews:feed" d'un serveur backend vers un autre.
 *
 * Pourquoi ce plugin existe :
 * Le canal legacy "BungeeCord"/"Forward", que Velocity expose par compatibilité,
 * a un bug connu qui l'empêche de relayer correctement des messages vers un sous-canal
 * personnalisé (voir https://github.com/PaperMC/Velocity/issues/1312). Résultat : les
 * messages envoyés par plugin-news-announcer via "Forward" n'arrivent jamais à
 * plugin-news-lobby, silencieusement, sans aucune erreur ni côté monde ni côté lobby.
 *
 * On contourne complètement ce bug en utilisant un canal moderne namespacé
 * ("pluginnews:feed"), enregistré explicitement via l'API de Velocity, et en relayant
 * nous-mêmes le message vers le bon serveur — indépendamment du réglage
 * "bungee-plugin-message-channel" de velocity.toml.
 */
@Plugin(
        id = "plugin-news-proxy",
        name = "PluginNewsProxy",
        version = "1.5.0",
        description = "Relaie pluginnews:feed entre sous-serveurs (contournement du bug Velocity #1312)"
)
public class PluginNewsProxy {

    private static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("pluginnews", "feed");

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public PluginNewsProxy(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("[PluginNewsProxy] Canal pluginnews:feed enregistré, relai actif entre sous-serveurs.");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        logger.info("[PluginNewsProxy] Message reçu sur pluginnews:feed, source="
                + event.getSource().getClass().getSimpleName());

        // On ne relaie que ce qui vient d'un serveur backend (pas d'un client), et on
        // empêche Velocity d'essayer de le retransmettre lui-même par un autre chemin.
        if (!(event.getSource() instanceof ServerConnection sourceConn)) {
            logger.warn("[PluginNewsProxy] Message ignoré : la source n'est pas un serveur backend ("
                    + event.getSource().getClass().getSimpleName() + ").");
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            // Format du message : [UTF nom-du-serveur-cible][octets bruts du JSON de nouveautés]
            // écrit ainsi par LobbyAnnouncer côté plugin-news-announcer.
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
            String targetServerName = in.readUTF();

            ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
            in.transferTo(payloadOut);
            byte[] payload = payloadOut.toByteArray();

            logger.info("[PluginNewsProxy] Relai depuis \"" + sourceConn.getServerInfo().getName()
                    + "\" vers \"" + targetServerName + "\" (" + payload.length + " octets de payload).");

            Optional<RegisteredServer> target = server.getServer(targetServerName);
            if (target.isEmpty()) {
                logger.warn("[PluginNewsProxy] Serveur cible \"" + targetServerName
                        + "\" introuvable dans la config Velocity, message ignoré. Serveurs connus : "
                        + server.getAllServers().stream().map(s -> s.getServerInfo().getName()).toList());
                return;
            }

            boolean sent = target.get().sendPluginMessage(CHANNEL, payload);
            logger.info("[PluginNewsProxy] sendPluginMessage vers \"" + targetServerName
                    + "\" -> " + (sent ? "envoyé" : "ÉCHEC (aucun joueur connecté sur ce serveur ?)"));
        } catch (Exception e) {
            logger.warn("[PluginNewsProxy] Erreur de relai du message pluginnews:feed : " + e.getMessage());
        }
    }
}
