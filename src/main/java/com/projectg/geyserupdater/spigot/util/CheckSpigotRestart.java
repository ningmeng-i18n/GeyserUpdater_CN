package com.projectg.geyserupdater.spigot.util;

import com.projectg.geyserupdater.common.logger.UpdaterLogger;
import com.projectg.geyserupdater.common.util.OsUtils;
import com.projectg.geyserupdater.common.util.ScriptCreator;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class CheckSpigotRestart {
    /**
     * Run {@link ScriptCreator#createRestartScript(boolean)} if an existing restart script is not defined in spigot.yml
     */
    public static void checkYml() {
        UpdaterLogger logger = UpdaterLogger.getLogger();
        // Do this early just as a check
        String scriptName;
        if (OsUtils.isWindows()) {
            scriptName = "ServerRestartScript.bat";
        } else if (OsUtils.isLinux() || OsUtils.isMacos()) {
            scriptName = "./ServerRestartScript.sh";
        } else {
            logger.warn("您的操作系统不受支持！GeyserUpdater 仅支持为 Linux、macOS 和 Windows 自动生成脚本。 ");
            return;
        }
        FileConfiguration spigotConfigurationYamlFile = YamlConfiguration.loadConfiguration(new File(new File("").getAbsolutePath(), "spigot.yml"));
        String scriptPath = spigotConfigurationYamlFile.getString("settings.restart-script");
        File script = new File(scriptPath);
        if (script.exists()) {
            logger.info("检测到已存在的重启脚本！");
        } else {
            try {
                // Tell the createScript method that a loop is not necessary because spigot has a restart system.
                ScriptCreator.createRestartScript(false);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            // Set the restart-script entry in spigot.yml to the one we just created
            spigotConfigurationYamlFile.set("settings.restart-script", scriptName);
            try {
                spigotConfigurationYamlFile.save("spigot.yml");
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            logger.warn("spigot.yml 中的配置值 'restart-script' 已设置为 " + scriptName);
            logger.warn("您必须重启服务器以便重启功能生效！ ");
        }
    }
}
