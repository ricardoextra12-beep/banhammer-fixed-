package com.example.banhammer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Leest ops.json (vanilla operators). */
public final class Ops {
	private static final Path OPS_FILE = Path.of("ops.json");

	private Ops() {
	}

	/** TRUE = staat in ops.json, FALSE = staat er niet in, null = bestand niet te lezen. */
	private static Boolean lookup(ServerPlayer player) {
		String uuid = player.getUUID().toString();
		String name = player.getGameProfile().name();
		try (Reader reader = Files.newBufferedReader(OPS_FILE)) {
			JsonArray ops = JsonParser.parseReader(reader).getAsJsonArray();
			for (JsonElement element : ops) {
				JsonObject op = element.getAsJsonObject();
				if (op.has("uuid") && op.get("uuid").getAsString().equalsIgnoreCase(uuid)) {
					return Boolean.TRUE;
				}
				if (op.has("name") && op.get("name").getAsString().equalsIgnoreCase(name)) {
					return Boolean.TRUE;
				}
			}
			return Boolean.FALSE;
		} catch (NoSuchFileException e) {
			return Boolean.FALSE;
		} catch (Exception e) {
			BanhammerMod.LOGGER.warn("Kon ops.json niet lezen", e);
			return null;
		}
	}

	/** Voor BESCHERMING: bij een leesfout geldt iedereen als operator (veilig). */
	public static boolean isOperator(ServerPlayer player) {
		Boolean result = lookup(player);
		return result == null || result;
	}

	/** Voor RECHTEN GEVEN: bij een leesfout krijgt niemand rechten (veilig). */
	public static boolean isOperatorStrict(ServerPlayer player) {
		return Boolean.TRUE.equals(lookup(player));
	}
}
