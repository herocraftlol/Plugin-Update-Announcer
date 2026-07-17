package com.example.newsannouncer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpdateDetector {

    public List<PluginUpdate> diff(Map<String, String> previous, Map<String, String> current) {
        List<PluginUpdate> updates = new ArrayList<>();

        for (var entry : current.entrySet()) {
            String name = entry.getKey();
            String newVersion = entry.getValue();
            String oldVersion = previous.get(name);

            if (oldVersion == null) {
                updates.add(new PluginUpdate(name, PluginUpdate.Type.ADDED, null, newVersion));
            } else if (!oldVersion.equals(newVersion)) {
                updates.add(new PluginUpdate(name, PluginUpdate.Type.UPDATED, oldVersion, newVersion));
            }
        }

        for (String name : previous.keySet()) {
            if (!current.containsKey(name)) {
                updates.add(new PluginUpdate(name, PluginUpdate.Type.REMOVED, previous.get(name), null));
            }
        }

        return updates;
    }
}
