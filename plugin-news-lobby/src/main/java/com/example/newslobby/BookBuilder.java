package com.example.newslobby;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formate une liste de NewsEntry en pages de livre lisibles, groupées par monde puis
 * par plugin, avec la date de la mise à jour.
 */
public class BookBuilder {

    // Limites empiriques confortables pour un affichage propre en jeu
    private static final int MAX_CHARS_PER_LINE = 20;
    private static final int MAX_LINES_PER_PAGE = 13;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM");

    public ItemStack build(List<NewsEntry> entries, String bookTitle) {
        List<String> pages = buildPages(entries);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(bookTitle);
        meta.setAuthor("Serveur");
        meta.setPages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private List<String> buildPages(List<NewsEntry> entries) {
        // Regroupement par monde, en conservant l'ordre chronologique décroissant (plus récent d'abord)
        Map<String, List<NewsEntry>> byWorld = new LinkedHashMap<>();
        entries.stream()
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .forEach(e -> byWorld.computeIfAbsent(e.world, k -> new ArrayList<>()).add(e));

        List<String> lines = new ArrayList<>();
        lines.add("§l§6Nouveautés\n§r");

        for (var worldEntry : byWorld.entrySet()) {
            lines.add("§n§b" + worldEntry.getKey() + "§r");
            for (NewsEntry e : worldEntry.getValue()) {
                lines.add(formatEntryHeader(e));
                if (e.changelog != null && !e.changelog.isBlank()) {
                    lines.addAll(wrap(cleanChangelog(e.changelog)));
                }
                lines.add(""); // ligne vide entre chaque entrée
            }
        }

        return paginate(lines);
    }

    private String formatEntryHeader(NewsEntry e) {
        String date = dateFormat.format(new Date(e.timestamp));
        return switch (e.type) {
            case "ADDED" -> "§a+ " + e.plugin + " §7(" + date + ")";
            case "REMOVED" -> "§c- " + e.plugin + " §7(" + date + ")";
            default -> "§e" + e.plugin + " §7" + e.oldVersion + "→" + e.newVersion + " (" + date + ")";
        };
    }

    private String cleanChangelog(String raw) {
        // Retire le markdown le plus courant (titres, listes, gras) pour un rendu propre en jeu
        return raw.replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("(?m)^[-*]\\s*", "• ")
                .trim();
    }

    private List<String> wrap(String text) {
        List<String> result = new ArrayList<>();
        for (String rawLine : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : rawLine.split(" ")) {
                if (current.length() + word.length() + 1 > MAX_CHARS_PER_LINE) {
                    result.add("§7" + current.toString().trim());
                    current = new StringBuilder();
                }
                current.append(word).append(" ");
            }
            if (!current.isEmpty()) result.add("§7" + current.toString().trim());
        }
        return result;
    }

    private List<String> paginate(List<String> lines) {
        List<String> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            if (lineCount >= MAX_LINES_PER_PAGE) {
                pages.add(currentPage.toString());
                currentPage = new StringBuilder();
                lineCount = 0;
            }
            currentPage.append(line).append("\n");
            lineCount++;
        }
        if (!currentPage.isEmpty()) pages.add(currentPage.toString());
        if (pages.isEmpty()) pages.add("§7Rien à afficher pour le moment.");

        return pages;
    }
}
