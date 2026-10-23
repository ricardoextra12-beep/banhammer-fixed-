package com.example.banhammer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

/** Bewaart twee punten in de Overworld: "spawn" en "room" (respawn-kamer). */
public final class Spawns {
	private static final Path FILE = Path.of("banhammer-spawn.json");

	public record Point(double x, double y, double z) {
	}

	private Spawns() {
	}

	private static JsonObject read() {
		try (Reader reader = Files.newBufferedReader(FILE)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (NoSuchFileException e) {
			return new JsonObject();
		} catch (Exception e) {
			BanhammerMod.LOGGER.warn("Kon banhammer-spawn.json niet lezen", e);
			return new JsonObject();
		}
	}

	public static Point get(String key) {
		JsonObject root = read();
		if (!root.has(key)) {
			return null;
		}
		JsonObject p = root.getAsJsonObject(key);
		return new Point(p.get("x").getAsDouble(), p.get("y").getAsDouble(), p.get("z").getAsDouble());
	}

	public static void set(String key, Point point) {
		JsonObject root = read();
		JsonObject p = new JsonObject();
		p.addProperty("x", point.x());
		p.addProperty("y", point.y());
		p.addProperty("z", point.z());
		root.add(key, p);
		try {
			Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
		} catch (IOException e) {
			BanhammerMod.LOGGER.warn("Kon banhammer-spawn.json niet opslaan", e);
		}
	}

	/** Commando om een speler (of selector zoals @a) naar het punt te sturen; null als het punt niet is ingesteld. */
	public static String tpCommand(String target, String key) {
		Point p = get(key);
		if (p == null) {
			return null;
		}
		return String.format(Locale.ROOT, "execute in minecraft:overworld run tp %s %.2f %.2f %.2f",
				target, p.x(), p.y(), p.z());
	}
}
