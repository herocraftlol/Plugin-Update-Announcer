package com.example.newsannouncer;

public class PluginUpdate {

    /**
     * RECENT : cas particulier — la version installée localement n'a PAS changé entre deux
     * scans (donc pas un ADDED/UPDATED "classique"), mais la release GitHub correspondant à
     * cette version a été publiée récemment (voir github.recent-release-check dans la config).
     * oldVersion reste null dans ce cas ; newVersion = version actuellement installée.
     */
    public enum Type { ADDED, UPDATED, REMOVED, RECENT }

    public final String pluginName;
    public final Type type;
    public final String oldVersion; // null si ADDED
    public final String newVersion; // null si REMOVED
    public String changelog;        // rempli plus tard par un ChangelogFetcher, peut rester null

    public PluginUpdate(String pluginName, Type type, String oldVersion, String newVersion) {
        this.pluginName = pluginName;
        this.type = type;
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }
}
