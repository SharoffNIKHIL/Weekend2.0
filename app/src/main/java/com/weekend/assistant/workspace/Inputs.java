package com.weekend.assistant.workspace;

/** Input checks shared by the workspace services. Messages name the field, never echo its content. */
public final class Inputs {

    private Inputs() {}

    public static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return limited(value, field, max);
    }

    public static String optional(String value, String field, int max) {
        return value == null || value.isBlank() ? null : limited(value, field, max);
    }

    private static String limited(String value, String field, int max) {
        String t = value.strip();
        if (t.length() > max) {
            throw new IllegalArgumentException(field + " is longer than " + max + " characters");
        }
        return t;
    }
}
