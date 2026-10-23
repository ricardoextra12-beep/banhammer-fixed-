package com.example.banhammer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inventaris-templates in banhammer-templates.json.
 * Opgeslagen per slot: item-id en aantal (enchants en eigen namen worden niet bewaard).
 */
public final class Templates {
	private static final Path FILE = Path.of("banhammer-templates.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public record Entry(int slot, String id, int count) {
	}

	private Templates() {
	}

	public static Map<String, List<Entry>> load() {
		Map<String, List<Entry>> result = new TreeMap<>();
		try (Reader reader = Files.newBufferedReader(FILE)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> template : root.entrySet()) {
				List<Entry> entries = new ArrayList<>();
				for (JsonElement element : template.getValue().getAsJsonArray()) {
					JsonObject o = element.getAsJsonObject();
					entries.add(new Entry(o.get("slot").getAsInt(), o.get("id").getAsString(), o.get("count").getAsInt()));
				}
				result.put(template.getKey(), entries);
			}
		} catch (NoSuchFileException e) {
			// nog geen templates
		} catch (Exception e) {
			BanhammerMod.LOGGER.warn("Kon banhammer-templates.json niet lezen", e);
		}
		return result;
	}

	private static void save(Map<String, List<Entry>> templates) {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<Entry>> template : templates.entrySet()) {
			JsonArray array = new JsonArray();
			for (Entry entry : template.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("slot", entry.slot());
				o.addProperty("id", entry.id());
				o.addProperty("count", entry.count());
				array.add(o);
			}
			root.add(template.getKey(), array);
		}
		try {
			Files.writeString(FILE, GSON.toJson(root));
		} catch (IOException e) {
			BanhammerMod.LOGGER.warn("Kon banhammer-templates.json niet opslaan", e);
		}
	}

	/** Slaat de inventaris van de speler op als "template-N" en geeft die naam terug. */
	public static String saveFrom(ServerPlayer player) {
		Map<String, List<Entry>> templates = load();
		int n = templates.size() + 1;
		while (templates.containsKey("template-" + n)) {
			n++;
		}
		String name = "template-" + n;

		List<Entry> entries = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			entries.add(new Entry(i, id, stack.getCount()));
		}
		templates.put(name, entries);
		save(templates);
		return name;
	}

	public static void delete(String name) {
		Map<String, List<Entry>> templates = load();
		templates.remove(name);
		save(templates);
	}

	/** Alle commando's om een template toe te passen (eerst de inventaris leegmaken). */
	public static List<String> commands(String templateName, String targetName) {
		List<String> commands = new ArrayList<>();
		List<Entry> entries = load().get(templateName);
		if (entries == null) {
			return commands;
		}
		commands.add("clear " + targetName);
		for (Entry entry : entries) {
			String slot = slotName(entry.slot());
			if (slot != null) {
				commands.add("item replace entity " + targetName + " " + slot + " with " + entry.id() + " " + entry.count());
			}
		}
		return commands;
	}

	private static String slotName(int index) {
		if (index >= 0 && index < 9) {
			return "hotbar." + index;
		}
		if (index >= 9 && index < 36) {
			return "inventory." + (index - 9);
		}
		return switch (index) {
			case 36 -> "armor.feet";
			case 37 -> "armor.legs";
			case 38 -> "armor.chest";
			case 39 -> "armor.head";
			case 40 -> "weapon.offhand";
			default -> null;
		};
	}
}
