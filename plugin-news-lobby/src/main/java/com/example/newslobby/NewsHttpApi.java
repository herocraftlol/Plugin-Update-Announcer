package com.example.newslobby;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Petit serveur HTTP embarqué (basé uniquement sur le JDK, aucune dépendance tierce) qui
 * expose le journal de nouveautés à ton site web, sans que celui-ci ait besoin d'interroger
 * chaque sous-serveur individuellement :
 *
 *   GET /news/recent?days=7&limit=7   -> les dernières nouveautés (7 jours / 7 entrées par défaut)
 *   GET /news/date?date=AAAA-MM-JJ    -> le journal des nouveautés d'un jour précis
 *
 * Si "api-key" est configuré, les requêtes doivent inclure l'en-tête "X-API-Key" correspondant.
 */
public class NewsHttpApi {

    private static final Logger LOGGER = Logger.getLogger("PluginNewsLobby");
    private static final Gson GSON = new Gson();

    private final NewsFeedStore feedStore;
    private final int defaultWindowDays;
    private final String apiKey;
    private HttpServer server;

    public NewsHttpApi(NewsFeedStore feedStore, int defaultWindowDays, String apiKey) {
        this.feedStore = feedStore;
        this.defaultWindowDays = defaultWindowDays;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/news/recent", this::handleRecent);
            server.createContext("/news/date", this::handleDate);
            server.setExecutor(null); // exécuteur par défaut, suffisant pour ce volume de trafic
            server.start();
            LOGGER.info("[PluginNewsLobby] API HTTP démarrée sur le port " + port
                    + " (/news/recent, /news/date).");
        } catch (IOException e) {
            LOGGER.warning("[PluginNewsLobby] Impossible de démarrer l'API HTTP sur le port "
                    + port + " : " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleRecent(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        int days = parseIntOrDefault(query.get("days"), defaultWindowDays);
        int limit = parseIntOrDefault(query.get("limit"), 7);

        List<NewsEntry> entries = feedStore.getRecent(days, limit);
        writeJson(exchange, 200, entries);
    }

    private void handleDate(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String dateParam = query.get("date");
        if (dateParam == null) {
            writeJson(exchange, 400, Map.of("error", "Paramètre 'date' manquant (format AAAA-MM-JJ)."));
            return;
        }

        try {
            LocalDate date = LocalDate.parse(dateParam);
            List<NewsEntry> entries = feedStore.getForDate(date);
            writeJson(exchange, 200, entries);
        } catch (DateTimeParseException e) {
            writeJson(exchange, 400, Map.of("error", "Date invalide, format attendu AAAA-MM-JJ."));
        }
    }

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (apiKey.isBlank()) return true;
        String provided = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey.equals(provided)) return true;

        writeJson(exchange, 401, Map.of("error", "Clé API manquante ou invalide (en-tête X-API-Key)."));
        return false;
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return result;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
