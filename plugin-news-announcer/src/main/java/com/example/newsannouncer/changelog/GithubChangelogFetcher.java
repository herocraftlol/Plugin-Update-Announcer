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
import java.util.logging.Logger;

/**
 * Récupère les notes de version (body de la release GitHub) correspondant à la nouvelle
 * version détectée d'un plugin.
 *
 * Fonctionnement :
 *  1) Essaie d'abord un match direct sur le tag "{tagPrefix}{newVersion}" (ex: "v1.2.3")
 *  2) Si ça échoue (tag introuvable, format différent d'un repo à l'autre),
 *     on liste les releases récentes et on cherche celle dont le tag CONTIENT la version.
 */
public class GithubChangelogFetcher implements ChangelogFetcher {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsAnnouncer");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String repo;       // ex: "EngineHub/WorldGuard"
    private final String tagPrefix;  // ex: "v" (peut être vide)
    private final String githubToken; // peut être vide

    public GithubChangelogFetcher(String repo, String tagPrefix, String githubToken) {
        this.repo = repo;
        this.tagPrefix = tagPrefix == null ? "" : tagPrefix;
        this.githubToken = githubToken;
    }

    @Override
    public String fetch(String pluginName, String oldVersion, String newVersion) throws Exception {
        // Tentative 1 : match exact sur le tag construit
        String directTag = tagPrefix + newVersion;
        String body = fetchReleaseByTag(directTag);
        if (body != null) return body;

        // Tentative 2 : parcourir les releases récentes et chercher un tag qui contient la version
        return fetchReleaseByFuzzyMatch(newVersion);
    }

    private String fetchReleaseByTag(String tag) throws Exception {
        HttpRequest request = buildRequest("https://api.github.com/repos/" + repo + "/releases/tags/" + tag);
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonElement body = json.get("body");
            return (body != null && !body.isJsonNull()) ? body.getAsString() : null;
        }
        return null; // 404 = ce tag n'existe pas, on tentera le fuzzy match
    }

    private String fetchReleaseByFuzzyMatch(String newVersion) throws Exception {
        HttpRequest request = buildRequest("https://api.github.com/repos/" + repo + "/releases?per_page=15");
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LOGGER.warning("[PluginNewsAnnouncer] Impossible de contacter l'API GitHub pour " + repo
                    + " (HTTP " + response.statusCode() + ")");
            return null;
        }

        JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
        for (JsonElement el : releases) {
            JsonObject release = el.getAsJsonObject();
            String tagName = release.get("tag_name").getAsString();
            if (tagName.contains(newVersion)) {
                JsonElement body = release.get("body");
                return (body != null && !body.isJsonNull()) ? body.getAsString() : null;
            }
        }
        return null; // aucune release ne correspond, tant pis, on annoncera sans détail
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
