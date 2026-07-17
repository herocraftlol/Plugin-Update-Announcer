package com.example.newsannouncer.announce;

import com.example.newsannouncer.PluginUpdate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

public class WebsiteAnnouncer {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsAnnouncer");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String apiUrl;
    private final String apiKey;

    public WebsiteAnnouncer(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    /**
     * Envoie le diff structuré en JSON, pour que le site puisse l'afficher comme il veut
     * (plutôt qu'un texte déjà formaté façon Discord).
     */
    public void send(String serverName, List<PluginUpdate> updates) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("server", serverName);
            payload.addProperty("timestamp", System.currentTimeMillis());

            JsonArray array = new JsonArray();
            for (PluginUpdate u : updates) {
                JsonObject entry = new JsonObject();
                entry.addProperty("plugin", u.pluginName);
                entry.addProperty("type", u.type.name());
                entry.addProperty("oldVersion", u.oldVersion);
                entry.addProperty("newVersion", u.newVersion);
                entry.addProperty("changelog", u.changelog);
                array.add(entry);
            }
            payload.add("updates", array);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 300) {
                            LOGGER.warning("[PluginNewsAnnouncer] Le site a répondu HTTP "
                                    + response.statusCode() + " : " + response.body());
                        }
                    });
        } catch (Exception e) {
            LOGGER.warning("[PluginNewsAnnouncer] Échec envoi site web : " + e.getMessage());
        }
    }
}
