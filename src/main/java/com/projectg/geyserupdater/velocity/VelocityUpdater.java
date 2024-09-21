package com.projectg.geyserupdater.velocity;

import com.projectg.geyserupdater.common.logger.UpdaterLogger;
import com.projectg.geyserupdater.common.util.FileUtils;
import com.projectg.geyserupdater.common.util.GeyserProperties;
import com.projectg.geyserupdater.common.util.ScriptCreator;
import com.projectg.geyserupdater.velocity.command.GeyserUpdateCommand;
import com.projectg.geyserupdater.velocity.listeners.VelocityJoinListener;
import com.projectg.geyserupdater.velocity.logger.Slf4jUpdaterLogger;
import com.projectg.geyserupdater.velocity.util.GeyserVelocityDownloader;
import com.projectg.geyserupdater.velocity.util.bstats.Metrics;

import com.google.inject.Inject;

import com.moandjiezana.toml.Toml;

import org.geysermc.geyser.GeyserImpl;
import org.slf4j.Logger;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Plugin(id = "geyserupdater", name = "GeyserUpdater", version = "1.6.4", description = "自动或手动下载新的 Geyser 版本并在服务器重启时应用。 ", authors = {"KejonaMC"},
        dependencies = {@Dependency(id = "geyser")})
public class VelocityUpdater {

    private static VelocityUpdater plugin;
    private final ProxyServer server;
    private final Logger baseLogger;
    private final Path dataDirectory;
    private final Toml config;
    private final Metrics.Factory metricsFactory;

    @Inject
    public VelocityUpdater(ProxyServer server, Logger baseLogger, @DataDirectory final Path folder, Metrics.Factory metricsFactory) {
        VelocityUpdater.plugin = this;
        this.server  = server;
        this.baseLogger = baseLogger;
        this.dataDirectory = folder;
        this.config = loadConfig(dataDirectory);
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        metricsFactory.make(this, 10673);
        new Slf4jUpdaterLogger(baseLogger);

        if (getConfig().getBoolean("Enable-Debug", false)) {
            UpdaterLogger.getLogger().info("尝试启用调试日志记录。 ");
            UpdaterLogger.getLogger().enableDebug();
        }

        checkConfigVersion();
        // todo: meta version checking

        // Register our only command
        server.getCommandManager().register("geyserupdate", new GeyserUpdateCommand());
        // Player alert if a restart is required when they join
        server.getEventManager().register(this, new VelocityJoinListener());

        // Make startup script if enabled
        if (config.getBoolean("Auto-Script-Generating")) {
            try {
                ScriptCreator.createRestartScript(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Auto update Geyser if enabled in the config
        if (config.getBoolean("Auto-Update-Geyser")) {
            scheduleAutoUpdate();
        }
        // Check if downloaded Geyser file exists periodically
        server.getScheduler()
                .buildTask(this, () -> {
                    FileUtils.checkFile("plugins/GeyserUpdater/BuildUpdate/Geyser-Velocity.jar", true);
                    UpdaterLogger.getLogger().info("已下载新的 Geyser 版本！请重启 Velocity 以使用更新后的版本！");
                })
                .delay(30L, TimeUnit.MINUTES)
                .repeat(12L, TimeUnit.HOURS)
                .schedule();
    }

    @Subscribe(order = PostOrder.LAST)
    public void onShutdown(ProxyShutdownEvent event) {
        // todo: listen for GeyserShutdownEvent instead
        if (!GeyserImpl.getInstance().isShuttingDown()) {
            throw new UnsupportedOperationException("不能在 Geyser 关闭前关闭 GeyserUpdater！不会应用任何更新。");
        }
        try {
            moveGeyserJar();
            for (int i = 0; i <= 2; i++) {
                try {
                    deleteGeyserJar();
                    break;
                } catch (Exception e) {
                    UpdaterLogger.getLogger().warn("尝试删除不必要的 Geyser jar 时发生错误！将再尝试删除 (2 - i) 次。 ");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException interruptException) {
                        UpdaterLogger.getLogger().error("多次尝试失败！", interruptException);
                    }
                }
            }
        } catch (IOException e) {
            UpdaterLogger.getLogger().error("尝试用新版本替换当前的 Geyser jar 时发生错误！放弃尝试。 ", e);
        }
    }

    /**
     * Load GeyserUpdater's config
     *
     * @param path The config's directory
     * @return The configuration
     */
    private Toml loadConfig(Path path) {
        File folder = path.toFile();
        File file = new File(folder, "config.toml");

        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
        }
        return new Toml().read(file);
    }

    /**
     * Check the config version of GeyserUpdater
     */
    public void checkConfigVersion() {
        //Change version number only when editing config.yml!
        if (getConfig().getLong("Config-Version", 0L).compareTo(2L) != 0) {
            UpdaterLogger.getLogger().warn("您的 config.yml 文件已过时。请删除它，并让系统生成一个新的 config.yml 文件！ ");
        }
    }

    /**
     * Check for a newer version of Geyser every 24hrs
     */
    public void scheduleAutoUpdate() {
        UpdaterLogger.getLogger().debug("尝试启用自动更新");
        // Checking for the build numbers of current build.
        // todo: build this in different way so that we don't repeat it if the Auto-Update-Interval is zero or -1 or something
        server.getScheduler()
                .buildTask(this, () -> {
                    UpdaterLogger.getLogger().debug("检查是否存在新的 Geyser 版本。 ");
                    try {
                        boolean isLatest = GeyserProperties.isLatestBuild();
                        if (!isLatest) {
                            UpdaterLogger.getLogger().info("有新的 Geyser 版本可用！正在尝试下载最新版本... ");
                            GeyserVelocityDownloader.updateGeyser();
                        }
                    } catch (Exception e) {
                        UpdaterLogger.getLogger().error("检查 Geyser 更新失败！我们无法连接到 Geyser 构建服务器，或者您的本地分支在服务器上不存在。 ", e);
                    }
                })
                .delay(1L, TimeUnit.MINUTES)
                .repeat(getConfig().getLong("Auto-Update-Interval", 24L), TimeUnit.HOURS)
                .schedule();
    }

    /**
     * Replace the Geyser jar in the plugin folder with the one in GeyserUpdater/BuildUpdate
     * Should only be called once Geyser has been disabled
     *
     * @throws IOException if there was an IO failure
     */
    public void moveGeyserJar() throws IOException {
        // Moving Geyser Jar to Plugins folder "Overwriting".
        File fileToCopy = new File("plugins/GeyserUpdater/BuildUpdate/Geyser-Velocity.jar");
        if (fileToCopy.exists()) {
            UpdaterLogger.getLogger().debug("将新的 Geyser jar 移动到 plugins 文件夹。 ");
            FileInputStream input = new FileInputStream(fileToCopy);
            File newFile = new File("plugins/Geyser-Velocity.jar");
            FileOutputStream output = new FileOutputStream(newFile);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }
            input.close();
            output.close();
        } else {
            UpdaterLogger.getLogger().debug("未找到新的 Geyser jar 文件以复制到 plugins 文件夹。 ");
        }
    }

    /**
     * Delete the Geyser jar in GeyserUpdater/BuildUpdate
     *
     * @throws IOException if it failed to delete
     */
    private void deleteGeyserJar() throws IOException {
        UpdaterLogger.getLogger().debug("如果 BuildUpdate 文件夹中存在 Geyser jar，则删除它。 ");
        Path file = Paths.get("plugins/GeyserUpdater/BuildUpdate/Geyser-Velocity.jar");
        Files.deleteIfExists(file);
    }

    public static VelocityUpdater getPlugin() {
        return plugin;
    }
    public ProxyServer getProxyServer() {
        return server;
    }
    public Path getDataDirectory() {
        return dataDirectory;
    }
    public Toml getConfig() {
        return config;
    }
}






