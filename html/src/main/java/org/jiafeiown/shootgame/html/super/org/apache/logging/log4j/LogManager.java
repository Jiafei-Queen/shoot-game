package org.apache.logging.log4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal stand-in for log4j-api's {@code LogManager}, used only by the GWT
 * build (see {@link Logger}). The desktop/Android builds keep the real
 * log4j2 pipeline untouched.
 */
public final class LogManager {

    private static final Map<String, Logger> loggers = new HashMap<String, Logger>();

    private LogManager() {
    }

    public static Logger getLogger() {
        return getLogger("root");
    }

    public static Logger getLogger(String name) {
        Logger logger = loggers.get(name);
        if (logger == null) {
            logger = new Logger(name);
            loggers.put(name, logger);
        }
        return logger;
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }
}
