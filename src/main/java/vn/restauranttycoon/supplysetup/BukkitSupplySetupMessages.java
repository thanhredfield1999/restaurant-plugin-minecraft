package vn.restauranttycoon.supplysetup;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BukkitSupplySetupMessages implements SupplySetupMessages {
    private static final String PREFIX = "supply-setup.";
    private final YamlConfiguration messages;

    public BukkitSupplySetupMessages(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public String text(String key, Map<String, String> placeholders) {
        String value = messages.getString(PREFIX + key);
        if (value == null) {
            throw new IllegalStateException("Missing message key: " + PREFIX + key);
        }
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            value = value.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return value;
    }
}
