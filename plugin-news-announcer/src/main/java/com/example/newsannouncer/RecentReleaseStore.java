package com.example.newsannouncer;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Retient, pour chaque plugin, la dernière version pour laquelle on a déjà signalé une
 * "release récente" (release GitHub publiée il y a moins de N jours, alors que la version
 * installée localement n'a pas changé entre deux scans). Sans ça, la même nouveauté serait
 * ré-annoncée à CHAQUE cycle de scan (toutes les `scan-interval-minutes`) tant qu'on reste
 * dans la fenêtre des N jours.
 */
public class RecentReleaseStore {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsAnnouncer");

    private final File file;
    private final YamlConfiguration yaml;

    public RecentReleaseStore(File dataFolder) {
        this.file = new File(dataFolder, "recent_release_announced.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean alreadyAnnounced(String pluginName, String version) {
        return version.equals(yaml.getString("plugins." + pluginName));
    }

    public void markAnnounced(String pluginName, String version) {
        yaml.set("plugins." + pluginName, version);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            LOGGER.warning("[PluginNewsAnnouncer] Impossible de sauvegarder recent_release_announced.yml : " + e.getMessage());
        }
    }
}
