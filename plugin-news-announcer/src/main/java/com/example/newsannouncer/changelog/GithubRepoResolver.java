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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Liste tous les repos publics d'un utilisateur/organisation GitHub une seule fois
 * (mis en cache pour la durée du cycle de détection), puis matche chaque plugin détecté
 * avec le repo dont le nom lui ressemble le plus.
 *
 * Ça évite d'avoir à déclarer chaque repo manuellement dans la config : il suffit
 * d'avoir renseigné le pseudo GitHub une fois.
 */
public class GithubRepoResolver {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsAnnouncer");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String username;
    private final String token;
    private Map<String, String> normalizedNameToRepo; // ex: "worldguard" -> "EngineHub/WorldGuard"

    public GithubRepoResolver(String username, String token) {
        this.username = username;
        this.token = token;
    }

    /**
     * @param pluginName nom exact du plugin (tel que dans plugin.yml)
     * @return "auteur/repo" si un match a été trouvé, sinon null
     */
    public String resolveRepo(String pluginName) {
        if (normalizedNameToRepo == null) {
            normalizedNameToRepo = fetchAllRepos();
        }
        String normalizedPlugin = normalize(pluginName);

        // 1) Match exact d'abord
        if (normalizedNameToRepo.containsKey(normalizedPlugin)) {
            return normalizedNameToRepo.get(normalizedPlugin);
        }

        // 2) Sinon, le repo dont le nom normalisé contient (ou est contenu dans) celui du plugin
        //    Ex: plugin "EssentialsX" -> repo "Essentials" ; plugin "Vault" -> repo "VaultAPI"
        for (var entry : normalizedNameToRepo.entrySet()) {
            if (entry.getKey().contains(normalizedPlugin) || normalizedPlugin.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null; // aucun repo ne correspond, tant pis
    }

    private Map<String, String> fetchAllRepos() {
        Map<String, String> result = new HashMap<>();
        int page = 1;

        try {
            while (true) {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/users/" + username + "/repos?per_page=100&page=" + page))
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .timeout(Duration.ofSeconds(10))
                        .GET();
                if (token != null && !token.isBlank()) {
                    builder.header("Authorization", "Bearer " + token);
                }

                HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOGGER.warning("[PluginNewsAnnouncer] Impossible de lister les repos GitHub de "
                            + username + " (HTTP " + response.statusCode() + ")");
                    break;
                }

                JsonArray repos = JsonParser.parseString(response.body()).getAsJsonArray();
                if (repos.isEmpty()) break; // plus de pages

                for (JsonElement el : repos) {
                    JsonObject repo = el.getAsJsonObject();
                    String fullName = repo.get("full_name").getAsString(); // "auteur/repo"
                    String repoName = repo.get("name").getAsString();
                    result.put(normalize(repoName), fullName);
                }

                if (repos.size() < 100) break; // dernière page
                page++;
            }
        } catch (Exception e) {
            LOGGER.warning("[PluginNewsAnnouncer] Erreur lors du listing des repos GitHub : " + e.getMessage());
        }

        return result;
    }

    private String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
