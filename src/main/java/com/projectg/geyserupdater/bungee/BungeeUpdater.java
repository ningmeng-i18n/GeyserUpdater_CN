package com.projectg.geyserupdater.bungee;

import com.projectg.geyserupdater.bungee.command.GeyserUpdateCommand;
import com.projectg.geyserupdater.bungee.listeners.BungeeJoinListener;
import com.projectg.geyserupdater.bungee.util.GeyserBungeeDownloader;
import com.projectg.geyserupdater.bungee.util.bstats.Metrics;
import com.projectg.geyserupdater.common.logger.JavaUtilUpdaterLogger;
import com.projectg.geyserupdater.common.logger.UpdaterLogger;
import com.projectg.geyserupdater.common.util.FileUtils;
import com.projectg.geyserupdater.common.util.GeyserProperties;
import com.projectg.geyserupdater.common.util.ScriptCreator;

import com.projectg.geyserupdater.common.util.SpigotResourceUpdateChecker;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public final class BungeeUpdater extends Plugin {

    private static BungeeUpdater plugin;
    private Configuration configuration;
    private UpdaterLogger logger;

    @Override
    public void onEnable() {
        plugin = this;
        logger = new JavaUtilUpdaterLogger(getLogger());
        new Metrics(this, 10203);

        this.loadConfig();
        if (getConfig().getBoolean("Enable-Debug", false)) {
            UpdaterLogger.getLogger().info("尝试启用调试……");
            UpdaterLogger.getLogger().enableDebug();
        }

        this.checkConfigVersion();
        // Check GeyserUpdater version
        this.checkUpdaterVersion();

        this.getProxy().getPluginManager().registerCommand(this, new GeyserUpdateCommand());
        // Player alert if a restart is required when they join
        getProxy().getPluginManager().registerListener(this, new BungeeJoinListener());

        // Make startup script
        if (configuration.getBoolean("Auto-Script-Generating")) {
            try {
                // Tell the createScript method that a loop is necessary because bungee has no restart system.
                ScriptCreator.createRestartScript(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Auto update Geyser if enabled
        if (configuration.getBoolean("Auto-Update-Geyser")) {
            scheduleAutoUpdate();
        }
        // Check if downloaded Geyser file exists periodically
        getProxy().getScheduler().schedule(this, () -> {
            if (FileUtils.checkFile("plugins/GeyserUpdater/BuildUpdate/Geyser-BungeeCord.jar", true)) {
                logger.info("新的 Geyser 版本已下载！请重启 BungeeCord 以使用更新后的版本！ ");
            }
        }, 30, 720, TimeUnit.MINUTES);

    }

    @Override
    public void onDisable() {
        // Force Geyser to disable so we can modify the jar in the plugins folder without issue
        logger.debug("强制 Geyser 先禁用... ");
        getProxy().getPluginManager().getPlugin("Geyser-BungeeCord").onDisable();
        try {
            moveGeyserJar();
            for (int i = 0; i <= 2; i++) {
                try {
                    deleteGeyserJar();
                    break;
                } catch (Exception e) {
                    logger.warn("尝试删除不必要的 Geyser jar 时发生错误！将再尝试 " + (2 - i) + " 次。 ");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interruptedException) {
                        logger.error("延迟过大，额外的尝试失败！", interruptedException);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("尝试用新版本的 Geyser jar 替换当前版本时发生错误！放弃替换。.", e);
        }
    }

    /**
     * Load GeyserUpdater's config, create it if it doesn't exist
     */
    public void loadConfig() {
        try {
            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(Config.startConfig(this, "config.yml"));
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Check the config version of GeyserUpdater
     */
    public void checkConfigVersion(){
        //Change version number only when editing config.yml!
         if (configuration.getInt("Config-Version", 0) != 2){
            logger.error("您的 config.yml 文件已过时。请删除它并允许生成一个新的 config.yml 文件!");
         }
    }

    /**
     * Check the version of GeyserUpdater against the spigot resource page
     */
    public void checkUpdaterVersion() {
        getProxy().getScheduler().runAsync(this, () -> {
            String pluginVersion = getDescription().getVersion();
            String latestVersion = SpigotResourceUpdateChecker.getVersion();
            if (latestVersion == null || latestVersion.length() == 0) {
                logger.error("获取最新 GeyserUpdater 版本失败！");
            } else {
                if (latestVersion.equals(pluginVersion)) {
                    logger.info("您正在使用最新版本的 GeyserUpdater！ ");
                } else {
                    logger.info("你正在用: " + pluginVersion + ". 新版本: "  + latestVersion + ". 前往链接下载最新版 https://www.spigotmc.org/resources/geyserupdater.88555/.");
                }
            }

        });
    }

    /**
     * Check for a newer version of Geyser every 24hrs
     */
    public void scheduleAutoUpdate() {
        UpdaterLogger.getLogger().debug("尝试启用自动更新 ");
        // todo: build this in different way so that we don't repeat it if the Auto-Update-Interval is zero or -1 or something
        getProxy().getScheduler().schedule(this, () -> {
            logger.debug("检查是否存在新的 Geyser 版本。 ");
            try {
                // Checking for the build numbers of current build.
                boolean isLatest = GeyserProperties.isLatestBuild();
                if (!isLatest) {
                    logger.info("有新的 Geyser 版本可用！正在尝试下载最新版本... ");
                    GeyserBungeeDownloader.updateGeyser();
                }
            } catch (Exception e) {
                logger.error("检查 Geyser 更新失败！无法连接到 Geyser 构建服务器，或者您的本地分支在服务器上不存在。 ", e);
            }
        }, 1, getConfig().getLong("Auto-Update-Interval", 24L) * 60, TimeUnit.MINUTES);
    }

    /**
     * Replace the Geyser jar in the plugin folder with the one in GeyserUpdater/BuildUpdate
     * Should only be called once Geyser has been disabled
     *
     * @throws IOException if there was an IO failure
     */
    public void moveGeyserJar() throws IOException {
        // Moving Geyser Jar to Plugins folder "Overwriting".
        File fileToCopy = new File("plugins/GeyserUpdater/BuildUpdate/Geyser-BungeeCord.jar");
        if (fileToCopy.exists()) {
            logger.debug("将新的 Geyser jar 移动到 plugins 文件夹……");
            FileInputStream input = new FileInputStream(fileToCopy);
            File newFile = new File("plugins/Geyser-BungeeCord.jar");
            FileOutputStream output = new FileOutputStream(newFile);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }
            input.close();
            output.close();
        } else {
            logger.debug("未找到可复制到 plugins 文件夹的新 Geyser jar。 ");
        }
    }

    /**
     * Delete the Geyser jar in GeyserUpdater/BuildUpdate
     *
     * @throws IOException If it failed to delete
     */
    private void deleteGeyserJar() throws IOException {
        UpdaterLogger.getLogger().debug("如果 BuildUpdate 文件夹中存在 Geyser jar，则删除它 ");
        Path file = Paths.get("plugins/GeyserUpdater/BuildUpdate/Geyser-BungeeCord.jar");
        Files.deleteIfExists(file);
    }
    public static BungeeUpdater getPlugin() {
        return plugin;
    }
    public Configuration getConfig() {
        return configuration;
    }
}
