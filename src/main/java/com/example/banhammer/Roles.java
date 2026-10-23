package com.example.banhammer;

import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side rollen opgeslagen in banhammer-roles.txt als spelersnaam=ROL. */
public final class Roles {
    private static final Path FILE = Path.of("banhammer-roles.txt");
    private Roles() {}

    private static synchronized Map<String, Role> read() {
        Map<String, Role> roles = new LinkedHashMap<>();
        try {
            if (Files.exists(FILE)) for (String line : Files.readAllLines(FILE)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                String[] pair = trimmed.split("=", 2);
                roles.put(pair[0].trim().toLowerCase(java.util.Locale.ROOT), Role.parse(pair[1].trim()));
            }
        } catch (Exception e) {
            BanhammerMod.LOGGER.warn("Kon banhammer-roles.txt niet lezen", e);
        }
        return roles;
    }

    private static synchronized void write(Map<String, Role> roles) {
        try {
            StringBuilder out = new StringBuilder("# Spelersrollen: spelersnaam=ROL\n");
            roles.forEach((name, role) -> out.append(name).append("=").append(role.name()).append("\n"));
            Files.writeString(FILE, out.toString());
        } catch (Exception e) {
            BanhammerMod.LOGGER.warn("Kon banhammer-roles.txt niet opslaan", e);
        }
    }

    public static Role of(ServerPlayer player) {
        String name = player.getGameProfile().name();
        Role assigned = read().get(name.toLowerCase(java.util.Locale.ROOT));
        if (assigned != null && assigned != Role.NONE) return assigned;
        return Ops.isOperator(player) ? Role.OWNER : Role.NONE;
    }

    public static boolean has(ServerPlayer player, Role needed) {
        return of(player).atLeast(needed);
    }

    public static boolean isProtected(ServerPlayer player) {
        return Ops.isOperator(player) || of(player).atLeast(Role.STAFF);
    }

    public static synchronized void set(String name, Role role) {
        Map<String, Role> roles = read();
        roles.put(name.toLowerCase(java.util.Locale.ROOT), role);
        write(roles);
    }

    public static synchronized void reset(String name) {
        Map<String, Role> roles = read();
        roles.remove(name.toLowerCase(java.util.Locale.ROOT));
        write(roles);
    }
}
