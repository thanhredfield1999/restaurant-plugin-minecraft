package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.concurrent.Executor;
import org.bukkit.plugin.java.JavaPlugin;

/** Executor chỉ enqueue task vào Bukkit primary server thread. */
public final class SupplyBukkitMainThreadExecutor implements Executor {
    private final JavaPlugin plugin;

    public SupplyBukkitMainThreadExecutor(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("plugin is disabled");
        }
        plugin.getServer().getScheduler().runTask(plugin, command);
    }
}

// Wire this only for Bukkit entity mutation. DB work remains on database.executor().
