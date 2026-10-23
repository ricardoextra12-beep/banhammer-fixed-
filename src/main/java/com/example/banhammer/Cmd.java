package com.example.banhammer;

import net.minecraft.server.MinecraftServer;

/** Voert servercommando's uit vanuit de serverconsole-context. */
public final class Cmd {
    private Cmd() {}

    public static void run(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) return;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    public static void runQuiet(MinecraftServer server, String command) {
        run(server, command);
    }
}
