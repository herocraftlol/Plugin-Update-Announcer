package com.example.newsannouncer;

public class PluginUpdate {

    public enum Type { ADDED, UPDATED, REMOVED }

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
