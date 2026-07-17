package com.example.newslobby;

public class NewsEntry {
    public String world;
    public String plugin;
    public String type;       // ADDED, UPDATED, REMOVED
    public String oldVersion;
    public String newVersion;
    public String changelog;
    public long timestamp;

    public NewsEntry() {}

    public NewsEntry(String world, String plugin, String type, String oldVersion,
                      String newVersion, String changelog, long timestamp) {
        this.world = world;
        this.plugin = plugin;
        this.type = type;
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
        this.changelog = changelog;
        this.timestamp = timestamp;
    }
}
