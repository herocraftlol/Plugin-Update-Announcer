package com.example.newslobby;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Stocke l'historique agrégé de toutes les nouveautés de tous les mondes dans un fichier JSON.
 * Les entrées trop anciennes sont automatiquement purgées à chaque sauvegarde pour éviter
 * une croissance infinie du fichier.
 */
public class NewsFeedStore {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsLobby");
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<NewsEntry>>() {}.getType();

    private final File file;
    private final long retentionMillis;

    public NewsFeedStore(File dataFolder, int retentionDays) {
        this.file = new File(dataFolder, "news_feed.json");
        this.retentionMillis = TimeUnit.DAYS.toMillis(retentionDays);
    }

    public synchronized List<NewsEntry> loadAll() {
        if (!file.exists()) return new ArrayList<>();
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            List<NewsEntry> entries = GSON.fromJson(content, LIST_TYPE);
            return entries != null ? entries : new ArrayList<>();
        } catch (IOException e) {
            LOGGER.warning("[PluginNewsLobby] Impossible de lire news_feed.json : " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized void append(List<NewsEntry> newEntries) {
        List<NewsEntry> all = loadAll();
        all.addAll(newEntries);

        long cutoff = System.currentTimeMillis() - retentionMillis;
        all.removeIf(e -> e.timestamp < cutoff);

        try {
            Files.writeString(file.toPath(), GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warning("[PluginNewsLobby] Impossible d'écrire news_feed.json : " + e.getMessage());
        }
    }
}
