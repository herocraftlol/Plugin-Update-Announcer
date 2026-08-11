package com.example.newslobby;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Journal agrégé de toutes les nouveautés de tous les sous-serveurs.
 *
 * Persisté dans un fichier JSON (survit aux redémarrages), mais aussi gardé en mémoire
 * (une simple List triée, rechargée une fois au démarrage puis tenue à jour à chaque
 * ajout) pour que les consultations par date ou par fenêtre récente soient instantanées,
 * sans relire le disque à chaque connexion de joueur ou requête HTTP.
 *
 * Les entrées trop anciennes sont automatiquement purgées à chaque sauvegarde pour éviter
 * une croissance infinie du fichier.
 */
public class NewsFeedStore {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsLobby");
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<NewsEntry>>() {}.getType();

    private final File file;
    private final long retentionMillis;
    private final ZoneId zone = ZoneId.systemDefault();

    // Copie en mémoire, toujours triée du plus récent au plus ancien.
    private final List<NewsEntry> memory = new ArrayList<>();

    public NewsFeedStore(File dataFolder, int retentionDays) {
        this.file = new File(dataFolder, "news_feed.json");
        this.retentionMillis = TimeUnit.DAYS.toMillis(retentionDays);
        reload();
    }

    /** (Re)charge la copie mémoire depuis le disque. Appelé une fois au démarrage. */
    public synchronized void reload() {
        memory.clear();
        memory.addAll(readFromDisk());
        memory.sort(Comparator.comparingLong((NewsEntry e) -> e.timestamp).reversed());
    }

    private List<NewsEntry> readFromDisk() {
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

    /** Liste complète actuellement en mémoire (plus récent en premier). */
    public synchronized List<NewsEntry> loadAll() {
        return new ArrayList<>(memory);
    }

    /** Toutes les entrées avec timestamp strictement postérieur à cutoffMillis. */
    public synchronized List<NewsEntry> getSince(long cutoffMillis) {
        return memory.stream().filter(e -> e.timestamp > cutoffMillis).toList();
    }

    /** Les `limit` dernières entrées des `days` derniers jours (pour le site web par ex.). */
    public synchronized List<NewsEntry> getRecent(int days, int limit) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        return memory.stream()
                .filter(e -> e.timestamp > cutoff)
                .limit(Math.max(limit, 0))
                .toList();
    }

    /** Journal : toutes les entrées survenues un jour calendaire précis (fuseau du serveur). */
    public synchronized List<NewsEntry> getForDate(LocalDate date) {
        return memory.stream()
                .filter(e -> Instant.ofEpochMilli(e.timestamp).atZone(zone).toLocalDate().equals(date))
                .toList();
    }

    public synchronized void append(List<NewsEntry> newEntries) {
        memory.addAll(0, newEntries);

        long cutoff = System.currentTimeMillis() - retentionMillis;
        memory.removeIf(e -> e.timestamp < cutoff);
        memory.sort(Comparator.comparingLong((NewsEntry e) -> e.timestamp).reversed());

        try {
            Files.writeString(file.toPath(), GSON.toJson(memory), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warning("[PluginNewsLobby] Impossible d'écrire news_feed.json : " + e.getMessage());
        }
    }
}
