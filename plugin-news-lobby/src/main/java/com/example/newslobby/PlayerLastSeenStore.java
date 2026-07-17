package com.example.newslobby;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Retient, pour chaque joueur, le timestamp de la dernière nouveauté qu'il a déjà vue.
 * Permet de ne montrer le livre automatiquement que si du contenu est réellement nouveau
 * pour lui.
 */
public class PlayerLastSeenStore {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsLobby");

    private final File file;
    private final YamlConfiguration yaml;

    public PlayerLastSeenStore(File dataFolder) {
        this.file = new File(dataFolder, "player_data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * @return le dernier timestamp vu, ou -1 si le joueur ne s'est jamais connecté avant
     *         (première connexion : on lui montrera uniquement les nouveautés < 7 jours).
     */
    public long getLastSeen(UUID playerId) {
        return yaml.getLong("players." + playerId, -1L);
    }

    public void setLastSeen(UUID playerId, long timestamp) {
        yaml.set("players." + playerId, timestamp);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            LOGGER.warning("[PluginNewsLobby] Impossible de sauvegarder player_data.yml : " + e.getMessage());
        }
    }
}
