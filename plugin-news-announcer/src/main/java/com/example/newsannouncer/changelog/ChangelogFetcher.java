package com.example.newsannouncer.changelog;

/**
 * Implémentée par chaque source possible de changelog (GitHub, Spigot, fichier manuel...).
 * Retourne null si aucun changelog n'a pu être trouvé (le plugin sera quand même annoncé,
 * juste sans détail).
 */
public interface ChangelogFetcher {
    String fetch(String pluginName, String oldVersion, String newVersion) throws Exception;
}
