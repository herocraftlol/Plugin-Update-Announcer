package com.example.newsannouncer.announce;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

public class DiscordAnnouncer {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsAnnouncer");
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String webhookUrl;

    public DiscordAnnouncer(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public void send(String message) {
        try {
            // Discord limite un message à 2000 caractères
            String truncated = message.length() > 1900 ? message.substring(0, 1900) + "\n[...]" : message;

            JsonObject payload = new JsonObject();
            payload.addProperty("content", truncated);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 300) {
                            LOGGER.warning("[PluginNewsAnnouncer] Discord a répondu HTTP "
                                    + response.statusCode() + " : " + response.body());
                        }
                    });
        } catch (Exception e) {
            LOGGER.warning("[PluginNewsAnnouncer] Échec envoi Discord : " + e.getMessage());
        }
    }
}
