package com.example.banhammer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Eenvoudig staff-logbestand in de hoofdmap van de server. */
public final class StaffLog {
    private static final Path FILE = Path.of("banhammer-staff.log");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private StaffLog() {}

    public static synchronized void record(String actor, String action) {
        try {
            Files.writeString(FILE, "[" + LocalDateTime.now().format(FORMAT) + "] " + actor + ": " + action + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            BanhammerMod.LOGGER.warn("Kon banhammer-staff.log niet schrijven", e);
        }
    }

    public static synchronized List<String> last(int amount) {
        try {
            List<String> lines = Files.exists(FILE) ? Files.readAllLines(FILE) : List.of();
            int from = Math.max(0, lines.size() - Math.max(0, amount));
            return lines.subList(from, lines.size());
        } catch (Exception e) {
            BanhammerMod.LOGGER.warn("Kon staff-log niet lezen", e);
            return List.of();
        }
    }
}
