package com.example.newsannouncer.changelog;

import java.io.File;
import java.nio.file.Files;

/**
 * Lit le changelog dans un fichier texte/markdown local, en prenant la section
 * qui suit un header du type "## <newVersion>" (format keep-a-changelog courant),
 * ou tout le fichier si aucun header n'est trouvé.
 */
public class ManualChangelogFetcher implements ChangelogFetcher {

    private final File changelogFile;

    public ManualChangelogFetcher(File changelogFile) {
        this.changelogFile = changelogFile;
    }

    @Override
    public String fetch(String pluginName, String oldVersion, String newVersion) throws Exception {
        if (!changelogFile.exists()) return null;

        String content = Files.readString(changelogFile.toPath());
        String[] lines = content.split("\n");

        StringBuilder section = new StringBuilder();
        boolean capturing = false;

        for (String line : lines) {
            boolean isHeader = line.trim().startsWith("#");
            if (isHeader && line.contains(newVersion)) {
                capturing = true;
                continue;
            }
            if (isHeader && capturing) {
                break; // on a atteint la section suivante
            }
            if (capturing) {
                section.append(line).append("\n");
            }
        }

        String result = section.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
