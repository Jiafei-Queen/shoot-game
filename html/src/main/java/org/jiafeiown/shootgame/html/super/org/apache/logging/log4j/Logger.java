package org.apache.logging.log4j;

import com.google.gwt.core.client.GWT;

/**
 * Minimal stand-in for log4j-api's {@code Logger}, used only by the GWT
 * build. GWT compiles from source and cannot translate log4j, so this
 * super-source class replaces the real interface; every call goes to the
 * browser console via {@code GWT.log}. The desktop/Android builds keep the
 * real log4j2 pipeline untouched.
 */
public class Logger {

    private final String name;

    Logger(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void trace(String msg) {
        log(msg);
    }

    public void trace(String msg, Object... args) {
        log(msg, args);
    }

    public void debug(String msg) {
        log(msg);
    }

    public void debug(String msg, Object... args) {
        log(msg, args);
    }

    public void info(String msg) {
        log(msg);
    }

    public void info(String msg, Object... args) {
        log(msg, args);
    }

    public void warn(String msg) {
        log(msg);
    }

    public void warn(String msg, Object... args) {
        log(msg, args);
    }

    public void error(String msg) {
        log(msg);
    }

    public void error(String msg, Object... args) {
        log(msg, args);
    }

    private void log(String msg, Object... args) {
        String body = (args == null || args.length == 0) ? msg : format(msg, args);
        GWT.log("[" + name + "] " + body);
    }

    /** Replaces {@code {}} placeholders with the given arguments. */
    private static String format(String msg, Object... args) {
        StringBuilder sb = new StringBuilder(msg.length() + 32);
        int arg = 0;
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c == '{' && i + 1 < msg.length() && msg.charAt(i + 1) == '}') {
                if (arg < args.length) {
                    sb.append(args[arg++]);
                } else {
                    sb.append("{}");
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
