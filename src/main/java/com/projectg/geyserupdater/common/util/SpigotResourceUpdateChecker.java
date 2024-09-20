package com.projectg.geyserupdater.common.util;

import com.projectg.geyserupdater.common.logger.UpdaterLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

public class SpigotResourceUpdateChecker {

    private static final String VERSION_REGEX = "(\\d+.){1,2}\\d+";

    /**
     * Get the latest version of GeyserUpdater from the spigot resource page
     * @return the latest version, null if there was an error.
     */
    public static String getVersion() {

        try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=88555").openStream(); Scanner scanner = new Scanner(inputStream)) {
            StringBuilder builder = new StringBuilder();
            while (scanner.hasNext()) {
                builder.append(scanner.next());
            }
            String version = builder.toString();
            if (version.matches(VERSION_REGEX)) {
                return version;
            } else {
                UpdaterLogger.getLogger().warn("在检查 Spigot 资源页面版本时获得了意外的字符串: " + version);
                return null;
            }
        } catch (IOException exception) {
            UpdaterLogger.getLogger().error("检查更新失败: " + exception.getMessage());
            return null;
        }
    }
}
