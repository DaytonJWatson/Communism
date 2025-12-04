package com.daytonjwatson.communism.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.daytonjwatson.communism.CommunismPlugin;

public class ResourceManager {

    private final CommunismPlugin plugin;
    private final Map<Material, Integer> pool;

    private File file;
    private FileConfiguration data;

    public ResourceManager(CommunismPlugin plugin) {
        this.plugin = plugin;
        this.pool = new EnumMap<>(Material.class);
    }

    public synchronized void load() {
        file = new File(plugin.getDataFolder(), "resources.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create resources.yml");
                e.printStackTrace();
            }
        }

        data = YamlConfiguration.loadConfiguration(file);
        for (String key : data.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat != null) {
                int amount = data.getInt(key, 0);
                if (amount > 0) {
                    pool.put(mat, amount);
                }
            }
        }
    }

    public synchronized void save() {
        if (data == null) return;
        data.getKeys(false).forEach(k -> data.set(k, null)); // clear old

        for (Map.Entry<Material, Integer> entry : pool.entrySet()) {
            if (entry.getValue() > 0) {
                data.set(entry.getKey().name(), entry.getValue());
            }
        }

        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save resources.yml");
            e.printStackTrace();
        }
    }

    public synchronized void add(Material material, int amount) {
        if (amount <= 0) return;
        pool.merge(material, amount, Integer::sum);
    }

    public synchronized int get(Material material) {
        return pool.getOrDefault(material, 0);
    }

    public synchronized Map<Material, Integer> getSnapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(pool));
    }

    public synchronized void remove(Material material, int amount) {
        if (amount <= 0) return;
        int current = pool.getOrDefault(material, 0);
        int next = current - amount;
        if (next <= 0) {
            pool.remove(material);
        } else {
            pool.put(material, next);
        }
    }

    public synchronized Set<Material> getStoredMaterials() {
        return pool.keySet();
    }
}
