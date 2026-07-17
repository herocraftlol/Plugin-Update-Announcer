package com.example.newsannouncer;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Scanne le dossier /plugins et extrait, pour chaque .jar, le nom et la version
 * déclarés dans son plugin.yml interne (sans avoir besoin de charger le plugin).
 */
public class PluginScanner {

    /**
     * @param pluginsFolder le dossier plugins/ du serveur
     * @return une map nomDuPlugin -> version
     */
    public Map<String, String> scan(File pluginsFolder) {
        Map<String, String> result = new LinkedHashMap<>();
        File[] files = pluginsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) return result;

        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                ZipEntry entry = jar.getEntry("plugin.yml");
                if (entry == null) continue;

                try (InputStream is = jar.getInputStream(entry)) {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(new InputStreamReader(is, StandardCharsets.UTF_8));

                    String name = yaml.getString("name");
                    String version = yaml.getString("version");
                    if (name != null && version != null) {
                        result.put(name, version);
                    }
                }
            } catch (Exception e) {
                // Jar corrompu ou plugin.yml illisible : on l'ignore silencieusement
                // pour ne pas bloquer le scan des autres plugins.
            }
        }
        return result;
    }
}
