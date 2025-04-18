package org.alfresco.utils;

/**
 * Utility class for JSON operations.
 */
public class JsonUtils {

    /**
     * Escapes special characters in a string for use in JSON.
     */
    public static String escape(String input) {
        if (input == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '/' -> result.append("\\/");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        result.append("\\u");
                        for (int j = 0; j < 4 - hex.length(); j++) {
                            result.append('0');
                        }
                        result.append(hex);
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }
}