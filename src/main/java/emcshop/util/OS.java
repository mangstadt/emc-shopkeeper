package emcshop.util;

/**
 * Determines the local operating system.
 */
public class OS {
    private static final String os;

    static {
        String value = System.getProperty("os.name");
        os = (value == null) ? "" : value.toLowerCase();
    }

    public static boolean isWindows() {
        return os.contains("windows");
    }

    public static boolean isMac() {
        return os.contains("mac");
    }

    public static boolean isLinux() {
        return os.contains("linux") || os.contains("unix");
    }
}
