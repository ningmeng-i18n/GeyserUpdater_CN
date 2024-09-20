package com.projectg.geyserupdater.common.util;

import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.projectg.geyserupdater.common.logger.UpdaterLogger;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {

    /**
     * Epoch time of that last occurrence that {@link #checkFile(String, boolean)} directly checked a file. Returns a value of 0 if the check file method has never been called.
     */
    private static long callTime = 0;

    /**
     * Returns a cached result of {@link #checkFile(String, boolean)}. Returns null if the method has never been called.
     */
    private static boolean cachedResult;

    // todo this is absolutely abhorrent and assumes we are always downloading the same jar

    /**
     * Check if a file exists.
     *
     * @param path the path of the file to test
     * @param allowCached allow a cached result of maximum 30 minutes to be returned
     * @return true if the file exists, false if not
     */
    public static boolean checkFile(String path, boolean allowCached) {
        UpdaterLogger logger = UpdaterLogger.getLogger();
        if (allowCached) {
            long elapsedTime = System.currentTimeMillis() - callTime;
            if (elapsedTime < 30 * 60 * 1000) {
                logger.debug("返回上次检查文件是否存在时缓存的结果。缓存的结果是: " + cachedResult);
                return cachedResult;
            } else {
                logger.debug("不返回上次检查文件是否存在时的缓存结果，因为时间已经过太久。 ");
            }
        }
        Path p = Paths.get(path);
        boolean exists = Files.exists(p);

        logger.debug("检查文件是否存在。结果是: " + exists);
        callTime = System.currentTimeMillis();
        cachedResult = exists;
        return exists;
    }

    /**
     * Download a file
     *
     * @param fileURL the url of the file
     * @param outputPath the path of the output file to write to
     * @param expectedSha256 the expected sha256 hash of the downloaded file
     */
    public static void downloadFile(String fileURL, String outputPath, @Nullable String expectedSha256) throws IOException {
        UpdaterLogger logger = UpdaterLogger.getLogger();
        logger.debug("尝试使用 URL 下载文件: " + fileURL + " ,保存到:   "+ outputPath);

        Path outputDirectory = Paths.get(outputPath).getParent();
        Files.createDirectories(outputDirectory);

        // Download Jar file
        URL url = new URL(fileURL);
        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
             FileOutputStream fos = new FileOutputStream(outputPath)) {

            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            logger.error("未能将 %s 下载到 %s ".formatted(fileURL, outputPath), e);
        }

        if (expectedSha256 != null) {
            // hash the file
            File file = new File(outputPath);
            ByteSource byteSource = com.google.common.io.Files.asByteSource(file);
            String hash = byteSource.hash(Hashing.sha256()).toString();

            // compare
            if (expectedSha256.equals(hash)) {
                if (logger.isDebug()) {
                    logger.debug("%s 的 %s 校验成功 ".formatted(file, hash));
                }
            } else {
                logger.warn("期望的哈希值为 %s，但实际得到的是 %s ".formatted(expectedSha256, hash));

                // If the checksum failed we attempt to delete the broken build.
                if (file.delete()) {
                    logger.warn("下载了一个校验不正确的 JAR 文件，正在删除： " + file);
                } else {
                    logger.error("未能删除一个不正确的下载文件，请手动删除： " + file);
                }
            }
        }
    }
}