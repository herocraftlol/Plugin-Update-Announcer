package com.example.newsannouncer.changelog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Récupère et AGRÈGE les notes de version (body des releases GitHub) de tout ce qui a
 * été publié depuis l'ancienne version installée, borné à une fenêtre de N jours
 * (par défaut 7). Contrairement à une simple récupération de la dernière release, ça
 * couvre le cas où plusieurs releases ont été publiées entre deux scans (ex: le serveur
 * était éteint, ou plusieurs versions sont sorties coup sur coup).
 *
 * Algorithme (les releases GitHub sont renvoyées triées du plus récent au plus ancien) :
 *  on parcourt la liste depuis le haut et on empile chaque release tant que :
 *   - elle est plus récente que la fenêtre (cutoff = maintenant - windowDays), ET
 *   - on n'a pas encore atteint le tag correspondant à l'ancienne version installée
 *  → dès qu'une des deux conditions n'est plus vraie, on s'arrête.
 */
public class GithubChangelogFetcher implements ChangelogFetcher {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsAnnouncer");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String repo;        // ex: "EngineHub/WorldGuard"
    private final String tagPrefix;   // ex: "v" (peut être vide)
    private final String githubToken; // peut être vide
    private final int windowDays;

    public GithubChangelogFetcher(String repo, String tagPrefix, String githubToken) {
        this(repo, tagPrefix, githubToken, 7);
    }

    public GithubChangelogFetcher(String repo, String tagPrefix, String githubToken, int windowDays) {
        this.repo = repo;
        this.tagPrefix = tagPrefix == null ? "" : tagPrefix;
        this.githubToken = githubToken;
        this.windowDays = windowDays;
    }

    @Override
    public String fetch(String pluginName, String oldVersion, String newVersion) throws Exception {
        List<JsonObject> releases = fetchRecentReleases();
        if (releases.isEmpty()) return null;

        String oldTagCandidateA = tagPrefix + (oldVersion == null ? "" : oldVersion);
        long cutoffMillis = Instant.now().toEpochMilli() - (windowDays * 24L * 60L * 60L * 1000L);

        List<JsonObject> collected = new ArrayList<>();
        for (JsonObject release : releases) {
            String tagName = release.get("tag_name").getAsString();
            long publishedAt = parsePublishedAt(release);

            // Borne 1 : on ne remonte pas plus loin que la fenêtre de N jours
            if (publishedAt < cutoffMillis) break;

            // Borne 2 : on s'arrête juste avant l'ancienne version (elle n'est pas incluse,
            // le joueur l'a déjà vue lors de la mise à jour précédente)
            if (oldVersion != null && (tagName.equals(oldTagCandidateA) || tagName.contains(oldVersion))) {
                break;
            }

            collected.add(release);
        }

        // Si rien collecté (ex: oldVersion == newVersion malgré tout, ou fenêtre trop courte),
        // on retombe sur la seule release correspondant à la nouvelle version, tant pis pour
        // l'agrégation, au moins on affiche quelque chose.
        if (collected.isEmpty()) {
            for (JsonObject release : releases) {
                String tagName = release.get("tag_name").getAsString();
                if (tagName.equals(tagPrefix + newVersion) || tagName.contains(newVersion)) {
                    collected.add(release);
                    break;
                }
            }
        }

        if (collected.isEmpty()) return null;

        return formatAggregatedChangelog(collected);
    }

    /**
     * @return le timestamp de publication (epoch millis) de la release GitHub correspondant
     *         EXACTEMENT à `version`, ou null si aucune release ne matche cette version.
     *         Utilisé pour vérifier si la version déjà installée localement est elle-même
     *         une release récente (voir github.recent-release-check dans la config).
     */
    public Long getPublishedAtForVersion(String version) throws Exception {
        List<JsonObject> releases = fetchRecentReleases();
        String directTag = tagPrefix + version;
        for (JsonObject release : releases) {
            String tagName = release.get("tag_name").getAsString();
            if (tagName.equals(directTag) || tagName.contains(version)) {
                long ts = parsePublishedAt(release);
                return ts == 0L ? null : ts;
            }
        }
        return null;
    }

    private String formatAggregatedChangelog(List<JsonObject> releases) {
        StringBuilder sb = new StringBuilder();
        boolean multiple = releases.size() > 1;

        for (JsonObject release : releases) {
            JsonElement bodyEl = release.get("body");
            String body = (bodyEl != null && !bodyEl.isJsonNull()) ? bodyEl.getAsString().trim() : "";
            if (body.isEmpty()) continue;

            if (multiple) {
                String tagName = release.get("tag_name").getAsString();
                String dateLabel = formatPublishedDate(release);
                sb.append("### ").append(tagName);
                if (dateLabel != null) sb.append(" (").append(dateLabel).append(")");
                sb.append("\n");
            }
            sb.append(body).append("\n\n");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private long parsePublishedAt(JsonObject release) {
        try {
            JsonElement el = release.get("published_at");
            if (el == null || el.isJsonNull()) return 0L;
            return Instant.parse(el.getAsString()).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private String formatPublishedDate(JsonObject release) {
        try {
            JsonElement el = release.get("published_at");
            if (el == null || el.isJsonNull()) return null;
            return DISPLAY_DATE.format(Instant.parse(el.getAsString()).atZone(java.time.ZoneId.systemDefault()));
        } catch (Exception e) {
            return null;
        }
    }

    private List<JsonObject> fetchRecentReleases() throws Exception {
        HttpRequest request = buildRequest("https://api.github.com/repos/" + repo + "/releases?per_page=50");
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOGGER.warning("[PluginNewsAnnouncer] Impossible de contacter l'API GitHub pour " + repo
                    + " (HTTP " + response.statusCode() + ")");
            return List.of();
        }

        List<JsonObject> result = new ArrayList<>();
        JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
        for (JsonElement el : releases) {
            result.add(el.getAsJsonObject());
        }
        return result;
    }

    private HttpRequest buildRequest(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(10))
                .GET();

        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
        return builder.build();
    }
}
