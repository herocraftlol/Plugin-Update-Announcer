package com.example.newsannouncer;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persiste la dernière liste connue de plugins+versions dans un fichier YAML,
 * pour pouvoir comparer au prochain démarrage.
 */
public class SnapshotStore {

    private final File file;

    public SnapshotStore(File dataFolder) {
        this.file = new File(dataFolder, "plugins_snapshot.yml");
    }

    public Map<String, String> load() {
        Map<String, String> result = new LinkedHashMap<>();
        if (!file.exists()) return result;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            result.put(key, yaml.getString(key));
        }
        return result;
    }

    public void save(Map<String, String> current) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : current.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
