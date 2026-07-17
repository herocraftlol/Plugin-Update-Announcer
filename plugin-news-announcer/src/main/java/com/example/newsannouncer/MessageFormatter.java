package com.example.newsannouncer;

import java.util.List;

public class MessageFormatter {

    public String formatForDiscord(String serverName, List<PluginUpdate> updates) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔌 **Mises à jour des plugins — ").append(serverName).append("**\n\n");

        for (PluginUpdate u : updates) {
            switch (u.type) {
                case ADDED -> sb.append("🟢 **Ajouté** : ").append(u.pluginName)
                        .append(" v").append(u.newVersion).append("\n");
                case REMOVED -> sb.append("🔴 **Retiré** : ").append(u.pluginName).append("\n");
                case UPDATED -> sb.append("🔄 **").append(u.pluginName).append("** ")
                        .append(u.oldVersion).append(" → ").append(u.newVersion).append("\n");
            }
            if (u.changelog != null && !u.changelog.isBlank()) {
                sb.append("> ").append(indentQuote(u.changelog)).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    public String formatForLobby(List<PluginUpdate> updates) {
        // Version courte pour le chat en jeu (pas de changelog détaillé, trop long pour un chat)
        StringBuilder sb = new StringBuilder("§b[Mises à jour] §f");
        boolean first = true;
        for (PluginUpdate u : updates) {
            if (!first) sb.append("§7, §f");
            first = false;
            switch (u.type) {
                case ADDED -> sb.append(u.pluginName).append(" §a(ajouté)");
                case REMOVED -> sb.append(u.pluginName).append(" §c(retiré)");
                case UPDATED -> sb.append(u.pluginName).append(" §e→ ").append(u.newVersion);
            }
        }
        return sb.toString();
    }

    private String indentQuote(String text) {
        // Pour que le markdown Discord ">" s'applique à chaque ligne du changelog
        return text.replace("\n", "\n> ");
    }
}
