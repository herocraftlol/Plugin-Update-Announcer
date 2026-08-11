package com.example.newslobby;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Retient, pour chaque joueur : le timestamp de la dernière nouveauté qu'il a déjà vue,
 * sa préférence d'affichage automatique du livre, et le dernier moment où le livre lui a
 * été ouvert automatiquement (pour éviter de le rouvrir à chaque reconnexion rapprochée).
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
     * @return le dernier timestamp vu, ou -1 si le joueur ne s'est jamais connecté avant.
     */
    public long getLastSeen(UUID playerId) {
        // Compatibilité avec l'ancien format (v1.0.x) où la valeur était stockée directement
        // sous "players.<uuid>" (un long) plutôt que sous "players.<uuid>.lastSeen".
        Object legacy = yaml.get("players." + playerId);
        if (legacy instanceof Number n) {
            return n.longValue();
        }
        return yaml.getLong("players." + playerId + ".lastSeen", -1L);
    }

    public void setLastSeen(UUID playerId, long timestamp) {
        yaml.set("players." + playerId + ".lastSeen", timestamp);
        save();
    }

    /** @return true si le joueur veut voir le livre s'ouvrir automatiquement à la connexion. */
    public boolean isAutoOpenEnabled(UUID playerId, boolean defaultValue) {
        return yaml.getBoolean("players." + playerId + ".autoOpen", defaultValue);
    }

    public void setAutoOpenEnabled(UUID playerId, boolean enabled) {
        yaml.set("players." + playerId + ".autoOpen", enabled);
        save();
    }

    /** @return le timestamp de la dernière ouverture automatique, ou -1 si jamais. */
    public long getLastAutoOpen(UUID playerId) {
        return yaml.getLong("players." + playerId + ".lastAutoOpen", -1L);
    }

    public void setLastAutoOpen(UUID playerId, long timestamp) {
        yaml.set("players." + playerId + ".lastAutoOpen", timestamp);
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
