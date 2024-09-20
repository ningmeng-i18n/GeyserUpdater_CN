package com.projectg.geyserupdater.spigot;

import com.projectg.geyserupdater.common.logger.JavaUtilUpdaterLogger;
import com.projectg.geyserupdater.common.logger.UpdaterLogger;
import com.projectg.geyserupdater.common.util.FileUtils;
import com.projectg.geyserupdater.common.util.GeyserProperties;
import com.projectg.geyserupdater.spigot.command.GeyserUpdateCommand;
import com.projectg.geyserupdater.spigot.listeners.SpigotJoinListener;
import com.projectg.geyserupdater.spigot.util.CheckSpigotRestart;
import com.projectg.geyserupdater.spigot.util.GeyserSpigotDownloader;
import com.projectg.geyserupdater.common.util.SpigotResourceUpdateChecker;
import com.projectg.geyserupdater.spigot.util.bstats.Metrics;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class SpigotUpdater extends JavaPlugin {
    private static SpigotUpdater plugin;

    @Override
    public void onEnable() {
        plugin = this;
        new JavaUtilUpdaterLogger(getLogger());
        new Metrics(this, 10202);

        loadConfig();
        if (getConfig().getBoolean("Enable-Debug", false)) {
            UpdaterLogger.getLogger().info("尝试启用调试日志记录。 汉化自柠檬汉化组:https://github.com/ningmeng-i18n");
            UpdaterLogger.getLogger().enableDebug();
        }

        checkConfigVersion();
        // Check our version
        checkUpdaterVersion();

        Objects.requireNonNull(getCommand("geyserupdate")).setExecutor(new GeyserUpdateCommand());
        getCommand("geyserupdate").setPermission("gupdater.geyserupdate");
        // Player alert if a restart is required when they join
        Bukkit.getServer().getPluginManager().registerEvents(new SpigotJoinListener(), this);

        // Check if a restart script already exists
        // We create one if it doesn't
        if (getConfig().getBoolean("Auto-Script-Generating")) {
            try {
                CheckSpigotRestart.checkYml();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // If true, start auto updating now and every 24 hours
        if (getConfig().getBoolean("Auto-Update-Geyser")) {
            scheduleAutoUpdate();
        }
        // Enable File Checking here. delay of 30 minutes and period of 12 hours (given in ticks)
        new BukkitRunnable() {

            @Override
            public void run() {
                if (FileUtils.checkFile("plugins/update/Geyser-Spigot.jar", false)) {
                    UpdaterLogger.getLogger().info("已下载新的 Geyser 版本！请重启服务器以使用更新后的版本！ ");
                }
            }
        }.runTaskTimerAsynchronously(this, 30 * 60 * 20, 12 * 60 * 60 * 20);
    }

    /**
     * Load GeyserUpdater's config, create it if it doesn't exist
     */
    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Check the config version of GeyserUpdater
     */
    public void checkConfigVersion() {
        //Change version number only when editing config.yml!
        if (getConfig().getInt("Config-Version", 0) != 2) {
            UpdaterLogger.getLogger().warn("您的 config.yml 文件已过时。请删除它，并让系统生成一个新的 config.yml 文件！ ");
        }
    }

    /**
     * Check the version of GeyserUpdater against the spigot resource page
     */
    public void checkUpdaterVersion() {
        UpdaterLogger logger = UpdaterLogger.getLogger();
        String pluginVersion = plugin.getDescription().getVersion();
        new BukkitRunnable() {
            @Override
            public void run() {
                String latestVersion = SpigotResourceUpdateChecker.getVersion();
                if (latestVersion == null || latestVersion.isEmpty()) {
                    logger.error("确定最新 GeyserUpdater 版本失败！");
                } else {
                    if (latestVersion.equals(pluginVersion)) {
                        logger.info("您正在使用最新版本的 GeyserUpdater！ ");
                    } else {
                        logger.info("正在用: " + pluginVersion + ". 新的可用: "  + latestVersion + ". 前往链接下载最新版 https://www.spigotmc.org/resources/geyserupdater.88555/.");
                    }
                }
            }
        }.runTaskAsynchronously(this);
    }

    /**
     * Check for a newer version of Geyser every 24hrs
     */
    public void scheduleAutoUpdate() {
        UpdaterLogger.getLogger().debug("尝试启用自动更新");
        // todo: build this in different way so that we don't repeat it if the Auto-Update-Interval is zero or -1 or something
        new BukkitRunnable() {

            @Override
            public void run() {
                UpdaterLogger.getLogger().debug("检查是否存在新的 Geyser 版本。");
                try {
                    boolean isLatest = GeyserProperties.isLatestBuild();
                    if (!isLatest) {
                        UpdaterLogger.getLogger().info("有新的 Geyser 版本可用！正在尝试下载最新版本... ");
                        GeyserSpigotDownloader.updateGeyser();
                    }
                } catch (Exception e) {
                    UpdaterLogger.getLogger().error("检查 Geyser 更新失败！我们无法连接到 Geyser 构建服务器，或者您的本地分支在服务器上不存在。 ", e);
                }
                // Auto-Update-Interval is in hours. We convert it into ticks
            }
        }.runTaskTimer(this, 60 * 20, getConfig().getLong("Auto-Update-Interval", 24L) * 60 * 60 * 20);
    }

    public static SpigotUpdater getPlugin() {
        return plugin;
    }
}
