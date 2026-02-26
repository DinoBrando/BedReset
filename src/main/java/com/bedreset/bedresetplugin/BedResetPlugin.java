package com.bedreset.bedresetplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.World;

public class BedResetPlugin extends JavaPlugin implements Listener {

    private boolean wasNight = false;
    private FileConfiguration config;
    private final Set<Player> usedThisNight = new HashSet<>();

    @Override
    public void onEnable() {

        saveDefaultConfig();
        config = getConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        // Reset once-per-night usage after night ends
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long time = Bukkit.getWorlds().get(0).getTime();
            boolean isNight = time >= 12541 && time <= 23458;

            if (wasNight && !isNight) {
                usedThisNight.clear();
            }

            wasNight = isNight;

        }, 20L, 20L);

        getLogger().info("BedReset plugin enabled.");
    }

    @EventHandler
    public void onBedClick(PlayerInteractEvent event) {

        if (!event.getAction().isRightClick()) {
            return; // allow breaking
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (!block.getType().name().contains("BED") || block.getType().name().equalsIgnoreCase("bedrock")) {
            return;
        }

        Player player = event.getPlayer();

        // Disable plugin in Nether or End (to avoid bed explosions)
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        // Permission check
        if (config.getBoolean("use-permission")
                && !player.hasPermission("bedreset.use")) {
            return;
        }

        boolean resetInDay = config.getBoolean("reset-in-daytime");
        boolean oncePerNight = config.getBoolean("once-per-night");
        boolean sendActionbar = config.getBoolean("send-actionbar");

        long time = player.getWorld().getTime();
        boolean isNight = time >= 12541 && time <= 23458;

        // Reset disabled during daytime
        if (!resetInDay && !isNight) {
            return;
        }

        // Cooldown logic
        if (oncePerNight && usedThisNight.contains(player)) {

            if (!isNight) {
                event.setCancelled(true); // stop daytime error message
            }

            if (sendActionbar) {
                sendActionbar(player,
                        config.getString("actionbar-message-failed", "&cYou're not tired."));
            }
            return;
        }

        if (oncePerNight) {
            usedThisNight.add(player);
        }

        // --- DAYTIME BEHAVIOR ---
        if (!isNight) {
            event.setCancelled(true);
            player.sleep(block.getLocation(), true); // fake animation only
        }

        // Reset insomnia stat
        player.setStatistic(Statistic.TIME_SINCE_REST, 0);

        // --- ACTIONBAR ---
        if (sendActionbar) {
            String msg = config.getString("actionbar-message-rested", "&aYou feel rested.");

            if (!isNight) {
                // Daytime → instant message
                sendActionbar(player, msg);

            } else {
                // Nighttime → delayed message to override vanilla "x/x sleeping"
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    sendActionbar(player, msg);
                }, 10L);
            }
        }
    }

    @EventHandler
    public void onRealSleep(PlayerBedEnterEvent event) {
        // Do NOT cancel real sleeping — vanilla behavior allowed
    }

    private void sendActionbar(Player player, String message) {
        player.sendActionBar(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand()
                        .deserialize(message)
        );
    }
}
