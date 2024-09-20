package com.projectg.geyserupdater.common.logger;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class JavaUtilUpdaterLogger implements UpdaterLogger {
    private final Logger logger;
    private Level originLevel;

    public JavaUtilUpdaterLogger(Logger logger) {
        this.logger = logger;
        UpdaterLogger.setLogger(this);
    }

    @Override
    public void error(String message) {
        logger.severe(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void debug(String message) {
        logger.fine(message);
    }

    @Override
    public void trace(String message) {
        logger.finer(message);
    }

    @Override
    public void enableDebug() {
        originLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        info("调试日志已启用");
    }

    @Override
    public void disableDebug() {
        if (originLevel != null) {
            logger.setLevel(originLevel);
            info("调试日志已启用");
        }
    }

    @Override
    public boolean isDebug() {
        return logger.getLevel() == Level.ALL;
    }
}
