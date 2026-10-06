package com.weekend.assistant.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Parses the UI's local date/time strings in the owner's time zone. */
final class Dates {

    private Dates() {}

    /** "2026-10-15T09:00" → instant; blank → null. */
    static Instant localDateTime(String value, ZoneId zone) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.strip()).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date-time must look like 2026-10-15T09:00");
        }
    }

    /** "2026-10-15" → 09:00 that day; blank → null. */
    static Instant localDate(String value, ZoneId zone) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip()).atTime(LocalTime.of(9, 0)).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must look like 2026-10-15");
        }
    }
}
