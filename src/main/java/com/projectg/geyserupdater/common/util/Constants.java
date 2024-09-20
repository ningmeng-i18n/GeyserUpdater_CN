package com.projectg.geyserupdater.common.util;

public class Constants {
    // Geyser 的基础下载 URL
    public static final String GEYSER_BASE_URL = "https://download.geysermc.org";

    // 获取 Geyser 最新 master 分支版本的端点
    public static final String GEYSER_LATEST_MASTER_ENDPOINT = "/v2/projects/geyser/versions/latest/builds/latest";

    // Geyser 下载链接
    public static final String GEYSER_DOWNLOAD_LINK = "/v2/projects/geyser/versions/latest/builds/latest/downloads/";

    // 开始检查更新的消息
    public static final String CHECK_START = "正在检查 Geyser 的更新...";

    // 当前使用的是最新版本的消息
    public static final String LATEST = "您正在使用 Geyser 的最新版本！";

    // 需要更新的消息
    public static final String OUTDATED = "有更新的 Geyser 版本可用！正在尝试下载最新版本...";

    // 检查更新失败的消息
    public static final String FAIL_CHECK = "检查 Geyser 更新失败！无法连接到 Geyser 构建服务器，或者您的本地分支在服务器上不存在。";
}
