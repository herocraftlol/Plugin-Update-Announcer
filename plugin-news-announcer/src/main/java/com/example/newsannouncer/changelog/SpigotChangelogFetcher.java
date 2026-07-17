package com.example.newsannouncer.changelog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SpigotMC n'a pas d'API officielle de changelog stable, on utilise donc l'API
 * communautaire Spiget (https://spiget.org) qui reflète les ressources Spigot.
 */
public class SpigotChangelogFetcher implements ChangelogFetcher {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final int resourceId;

    public SpigotChangelogFetcher(int resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public String fetch(String pluginName, String oldVersion, String newVersion) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spiget.org/v2/resources/" + resourceId + "/updates/latest"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("description")) return null;

        // La description Spiget contient souvent du BBCode/HTML basique, on nettoie grossièrement.
        String raw = json.get("description").getAsString();
        return raw.replaceAll("<[^>]*>", "").trim();
    }
}
