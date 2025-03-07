package de.jeff_media.angelchest;

import de.jeff_media.angelchest.config.Config;
import de.jeff_media.angelchest.data.AngelChest;
import de.jeff_media.angelchest.enums.PremiumFeatures;
import de.jeff_media.daddy.Daddy;
import de.jeff_media.jefflib.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

public final class Logger {

    private final Main main = Main.getInstance();
    final long maxOffsetBeforeRemoval;
    final String path;
    final double removeEveryXHours;
    final double removeOlderThanXHours;

    public Logger() {
        path = main.getDataFolder() + File.separator + "logs";
        if (!Files.isDirectory(new File(path).toPath())) {
            main.getLogger().info("Created log folder at " + path);
            new File(path).mkdirs();
        }
        removeOlderThanXHours = main.getConfig().getDouble(Config.PURGE_LOGS_OLDER_THAN_X_HOURS);
        removeEveryXHours = main.getConfig().getDouble(Config.PURGE_LOGS_EVERY_X_HOURS);

        final long ticksBetweenChecks = Ticks.fromHours(removeEveryXHours);
        maxOffsetBeforeRemoval = Ticks.fromHours(removeOlderThanXHours);
        if (removeEveryXHours != -1) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(main, this::purgeLogs, ticksBetweenChecks, ticksBetweenChecks);
        }
        purgeLogs();
    }

    public File getLogFile(final PlayerDeathEvent event) {

        return new File(path + File.separator + getLogFileName(event));
    }

    public File getLogFile(final String name) {
        return new File(path + File.separator + name);
    }

    public String getLogFileName(final PlayerDeathEvent event) {
        final String player = event.getEntity().getName();
        final String uuid = event.getEntity().getUniqueId().toString();
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        //String filename = String.format("%s_%s_%s",event.getEntity().getLocation().getWorld().getName(),player,timestamp);
        //return filename+".log";
        return main.getConfig().getString(Config.LOG_FILENAME).replaceAll("\\{player}", player).replaceAll("\\{uuid}", uuid).replaceAll("\\{world}", event.getEntity().getLocation().getWorld().getName()).replaceAll("\\{date}", timestamp);
    }

    private String loc2string(final Location location) {
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();
        final String world = location.getWorld().getName();
        return String.format("%d %d %d @ %s", x, y, z, world);
    }

    public void logDeath(final PlayerDeathEvent event, final AngelChest ac) {
        if (!Daddy.allows(PremiumFeatures.LOG_ANGELCHEST_TRANSACTIONS)) return;
        final File file = getLogFile(event);
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        write("=== AngelChest spawned ===", file);
        write("Player: " + event.getEntity().getName(), file);
        write("Player UUID: " + event.getEntity().getUniqueId().toString(), file);
        write("Death Time: " + timestamp, file);
        write("Death Location: " + loc2string(event.getEntity().getLocation()), file);
        write("Chest Location: " + loc2string(ac.block.getLocation()), file);
        write("Death Cause: " + event.getEntity().getLastDamageCause().getCause().name(), file);
        write("Player XP: " + event.getEntity().getExp(), file);
        write("Chest XP: " + ac.experience, file);
        write("Chest Protection: " + ac.isProtected, file);
        write("Chest Duration: " + (ac.infinite ? "infinite" : ac.secondsLeft + " seconds"), file);
        int lineNo = 1;
        for (final String line : ac.hologram.text.split("\n")) {
            write("Hologram Line " + lineNo + ": " + line, file);
            lineNo++;
        }
        write("", file);
        write("=== INFORMATION ===", file);
        write("Please note that some plugins remove certain items on death (soulbound items etc.), while other plugins add certain drops (player heads etc.). That's why this log file show you three different item lists:", file);
        write("1. The original inventory as it was when the player died. This does not include 3rd party plugin drops, but includes items that would have been removed on death, e.g. curse of vanishing items.", file);
        write("2. The items the player would have dropped if AngelChest wouldn't be used. This does include 3rd party plugin drops but does not include non-droppable items like curse of vanishing items.", file);
        write("3. The final AngelChest contents, which is a combination of 1. and 2. and does not include randomly lost items: it includes all custom drops and does not include non-droppable items.", file);
        write("", file);

        write("=== 1. Player Inventory ===", file);
        write("The player had the following items in his inventory at the time of his death:", file);
        for (final ItemStack item : event.getEntity().getInventory().getContents()) {
            if (item == null) continue;
            write("> " + item.toString(), file);
        }
        write("", file);
        write("=== 2. Player Drops ===", file);
        write("The player would have dropped the following items at the time of this death:", file);
        for (final ItemStack item : event.getDrops()) {
            if (item == null) continue;
            write("> " + item.toString(), file);
        }

        final Set<ItemStack> lostItems = ac.randomlyLostItems;
        if (lostItems != null && !lostItems.isEmpty()) {
            write("", file);
            write("=== Random Item loss ===", file);
            write("The following items were lost due to random item loss:", file);
            for (final ItemStack item : lostItems) {
                if (item == null) continue;
                write("- " + item.toString(), file);
            }
        }

        write("", file);
        write("=== 3. AngelChest inventory ===", file);
        write("The AngelChest contains the following items:", file);
        for (final ItemStack item : ac.storageInv) {
            if (item == null) continue;
            write("> " + item.toString(), file);
        }
        for (final ItemStack item : ac.armorInv) {
            if (item == null) continue;
            write("> " + item.toString(), file);
        }
        for (final ItemStack item : ac.extraInv) {
            if (item == null) continue;
            write("> " + item.toString(), file);
        }
        for (final ItemStack item : ac.overflowInv) {
            if (item == null) continue;
            write("> " + item.toString(), file);
        }
        write("", file);
    }

    public void logItemTaken(final Player player, @Nullable final ItemStack item, final File file) {
        if (!Daddy.allows(PremiumFeatures.GENERIC)) return; // Don't add feature here
        if (item == null) return;
        writeWithTime(String.format("Player \"%s\" took item: %s", player.getName(), item.toString()), file);
    }

    public void logLastItemTaken(final Player player, final File file) {
        if (!Daddy.allows(PremiumFeatures.GENERIC)) return; // Don't add feature here
        write("", file);
        writeWithTime(String.format("Player \"%s\" took the last item. Removing AngelChest!", player.getName()), file);
        write("", file);
        write("=== AngelChest removed ===", file);
    }

    public void logPaidForChest(final Player player, final double price, final File file) {
        if (!Daddy.allows(PremiumFeatures.GENERIC)) return; // Don't add feature here
        writeWithTime(String.format("Player \"%s\" paid %f to open this AngelChest for the first time.", player.getName(), price), file);
    }

    public void logXPTaken(final Player player, final int xp, final File file) {
        if (!Daddy.allows(PremiumFeatures.GENERIC)) return; // Don't add feature here
        writeWithTime(String.format("Player \"%s\" took XP: %d", player.getName(), xp), file);
    }

    public void purgeLogs() {
        if (removeOlderThanXHours == -1) return;
        main.debug("Checking for old log files...");
        final long now = new Date().getTime();
        final File logFolder = new File(path);
        int purged = 0;
        int purgedSuccessfully = 0;
        for (final File logfile : logFolder.listFiles()) {
            final long diff = now - logfile.lastModified();
            if (diff > maxOffsetBeforeRemoval) {
                main.debug("Deleting log file " + logfile.getName() + " because it is older than " + removeOlderThanXHours + " hours...");
                if (!logfile.delete()) {
                    main.getLogger().warning("Could not delete log file " + logfile.getName());
                } else {
                    purgedSuccessfully++;
                }
                purged++;
            }
        }
        if (purged > 0) {
            if (purged == purgedSuccessfully) {
                main.getLogger().info("Removed " + purged + " old log files.");
            } else {
                main.getLogger().warning("Attempted to remove " + purged + " old log files, but could only remove " + purgedSuccessfully);
            }
        }
    }

    private void write(final String text, final File file) {
        try {
            final FileWriter fw = new FileWriter(file, true);
            final BufferedWriter bw = new BufferedWriter(fw);
            bw.write(text);
            bw.newLine();
            bw.close();
        } catch (final IOException e) {
            main.getLogger().severe("Could not write to logfile " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }

    private void writeWithTime(final String text, final File file) {
        final FileWriter fw;
        final String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try {
            fw = new FileWriter(file, true);
            final BufferedWriter bw = new BufferedWriter(fw);
            bw.write(String.format("[%s] %s", timestamp, text));
            bw.newLine();
            bw.close();
        } catch (final IOException e) {
            main.getLogger().severe("Could not write to logfile " + file.getAbsolutePath());
            e.printStackTrace();
        }
    }
}
