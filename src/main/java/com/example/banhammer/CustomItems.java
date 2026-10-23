package com.example.banhammer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Zwaard-bouwer: materiaal, schade, effect bij slag en lore. Het zwaard is een normaal spel-item. */
public final class CustomItems {
	public static final String[] MATERIALS = {"iron", "diamond", "netherite"};
	public static final int[] DAMAGES = {8, 12, 20, 40};
	public static final String[] EFFECT_IDS = {"none", "slowness", "weakness", "glowing", "levitation", "lightning"};
	public static final String[] EFFECT_NAMES = {"Geen", "Vertraging", "Zwakte", "Gloeien", "Zweven", "Bliksem"};
	public static final String[] LORES = {
			"Gesmeed door het staff-team",
			"Alleen voor het event",
			"Een legendarisch wapen",
			"Gebruik met verstand"
	};

	/** Per speler: [materiaal, schade, effect, lore] (indexen in de lijsten hierboven). */
	private static final Map<UUID, int[]> CONFIGS = new HashMap<>();

	private CustomItems() {
	}

	public static int[] config(UUID id) {
		return CONFIGS.computeIfAbsent(id, key -> new int[]{1, 1, 0, 0});
	}

	/** field: 0 = materiaal, 1 = schade, 2 = effect, 3 = lore */
	public static void cycle(UUID id, int field) {
		int[] c = config(id);
		int size = switch (field) {
			case 0 -> MATERIALS.length;
			case 1 -> DAMAGES.length;
			case 2 -> EFFECT_IDS.length;
			default -> LORES.length;
		};
		c[field] = (c[field] + 1) % size;
	}

	public static String giveCommand(String playerName, int[] c) {
		int damage = DAMAGES[c[1]] - 1; // speler heeft zelf al 1 schade
		return "give " + playerName + " minecraft:" + MATERIALS[c[0]] + "_sword["
				+ "custom_name={text:\"Custom Zwaard\",color:\"gold\",italic:false},"
				+ "custom_data={bhsword:1b,effect:\"" + EFFECT_IDS[c[2]] + "\"},"
				+ "attribute_modifiers=["
				+ "{type:\"minecraft:attack_damage\",id:\"banhammer:damage\",amount:" + damage + ",operation:\"add_value\",slot:\"mainhand\"},"
				+ "{type:\"minecraft:attack_speed\",id:\"banhammer:speed\",amount:-2.4,operation:\"add_value\",slot:\"mainhand\"}"
				+ "],"
				+ "lore=[{text:\"" + LORES[c[3]] + "\",color:\"gray\",italic:false}]]";
	}

	/** Effect dat optreedt als je met het zwaard slaat. */
	public static void onHit(MinecraftServer server, Entity target, String effect) {
		String id = target.getUUID().toString();
		switch (effect) {
			case "slowness" -> Cmd.runQuiet(server, "effect give " + id + " minecraft:slowness 4 1 true");
			case "weakness" -> Cmd.runQuiet(server, "effect give " + id + " minecraft:weakness 6 0 true");
			case "glowing" -> Cmd.runQuiet(server, "effect give " + id + " minecraft:glowing 8 0 true");
			case "levitation" -> Cmd.runQuiet(server, "effect give " + id + " minecraft:levitation 2 0 true");
			case "lightning" -> Cmd.runQuiet(server, "execute at " + id + " run summon minecraft:lightning_bolt ~ ~ ~");
			default -> {
			}
		}
	}
}
