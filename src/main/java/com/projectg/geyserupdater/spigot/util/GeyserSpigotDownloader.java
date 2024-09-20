package com.projectg.geyserupdater.spigot.util;

import com.projectg.geyserupdater.common.logger.UpdaterLogger;
import com.projectg.geyserupdater.common.util.Constants;
import com.projectg.geyserupdater.common.util.FileUtils;
import com.projectg.geyserupdater.common.util.GeyserDownloadApi;
import com.projectg.geyserupdater.common.util.ServerPlatform;
import com.projectg.geyserupdater.spigot.SpigotUpdater;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GeyserSpigotDownloader {
    private static SpigotUpdater plugin;
    private static UpdaterLogger logger;

    /**
     * Download the latest build of Geyser from Jenkins CI for the currently used branch.
     * If enabled in the config, the server will also attempt to restart.
     */
    public static void updateGeyser() {
        plugin = SpigotUpdater.getPlugin();
        logger = UpdaterLogger.getLogger();

        UpdaterLogger.getLogger().debug("尝试下载新的 Geyser 版本。 汉化自柠檬汉化组:https://github.com/ningmeng-i18n");

        boolean doRestart = plugin.getConfig().getBoolean("Auto-Restart-Server");

        // Start the process async
        new BukkitRunnable() {
            @Override
            public void run() {
                // Download the newest build and store the success state
                boolean downloadSuccess = downloadGeyser();
                // No additional code should be run after the following BukkitRunnable
                // Run it synchronously because it isn't thread-safe
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (downloadSuccess) {
                            String successMsg = "最新的 Geyser 版本已下载！必须重启服务器以便使更改生效。 ";
                            logger.info(successMsg);
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.hasPermission("gupdater.geyserupdate")) {
                                    player.sendMessage(ChatColor.GREEN + successMsg);
                                }
                            }
                            if (doRestart) {
                                restartServer();
                            }
                        } else {
                            // fail messages are already sent to the logger in downloadGeyser()
                            String failMsg = "下载新的 Geyser 版本时发生错误()。请检查服务器控制台以获取更多信息！ ";
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.hasPermission("gupdater.geyserupdate")) {
                                    player.sendMessage(ChatColor.RED + failMsg);
                                }
                            }
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Internal code for downloading the latest build of Geyser from Jenkins CI for the currently used branch.
     *
     * @return true if the download was successful, false if not.
     */
    private static boolean downloadGeyser() {
        String fileUrl = Constants.GEYSER_BASE_URL + Constants.GEYSER_DOWNLOAD_LINK + ServerPlatform.SPIGOT.getUrlComponent();
        // todo: make sure we use the update folder defined in bukkit.yml (it can be changed)
        String outputPath = "plugins/update/Geyser-Spigot.jar";
        try {
            String expectedHash = new GeyserDownloadApi().data().downloads().spigot().sha256();
            FileUtils.downloadFile(fileUrl, outputPath, expectedHash);
        } catch (Exception e) {
            logger.error("下载最新的 Geyser 版本失败 ", e);
            return false;
        }

        if (!FileUtils.checkFile(outputPath, false)) {
            logger.error("未能找到已下载的 Geyser 版本！ ");
            return false;
        } else {
            return true;
        }
    }

    /**
     * Attempt to restart the server
     */
    private static void restartServer() {
        logger.warn("服务器将在 10 秒后重启！ ");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("Restart-Message-Players")));
        }
        // Attempt to restart the server 10 seconds after the message
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Object spigotServer;
                    try {
                        spigotServer = SpigotUpdater.getPlugin().getServer().getClass().getMethod("spigot").invoke(SpigotUpdater.getPlugin().getServer());
                    } catch (NoSuchMethodException e) {
                        logger.error("您没有运行 Spigot（或其分支，如 Paper）！GeyserUpdater 无法自动重启您的服务器！ ", e);
                        return;
                    }
                    Method restartMethod = spigotServer.getClass().getMethod("restart");
                    restartMethod.setAccessible(true);
                    restartMethod.invoke(spigotServer);
                } catch (NoSuchMethodException e) {
                    logger.error("您的服务器版本太旧，无法自动重启！ ", e);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    logger.error("重启服务器失败！ ", e);
                }
            }
        }.runTaskLater(plugin, 200); // 200 ticks is around 10 seconds (at 20 TPS)
    }
}