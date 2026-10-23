package com.example.banhammer;

import net.minecraft.server.MinecraftServer;

/** Het event "Raining Tacos": op een vast interval valt er een beloning uit de lucht bij elke speler. */
public final class Events {
	private static final int[] INTERVAL_TICKS = {100, 60, 30, 15};
	private static final String[] INTERVAL_NAMES = {"elke 5 s", "elke 3 s", "elke 1,5 s", "elke 0,75 s"};

	private static final String[] REWARD_NAMES = {"Taco", "Gouden taco", "Diamant", "Smaragd"};
	private static final String[] REWARD_ITEMS = {
			"{id:\"minecraft:cooked_beef\",count:1,components:{custom_name:{text:\"Taco\",color:\"yellow\",italic:false}}}",
			"{id:\"minecraft:golden_apple\",count:1,components:{custom_name:{text:\"Gouden Taco\",color:\"gold\",italic:false}}}",
			"{id:\"minecraft:diamond\",count:1}",
			"{id:\"minecraft:emerald\",count:1}"
	};

	private static boolean active = false;
	private static int counter = 0;
	private static int interval = 1;
	private static int reward = 0;

	private Events() {
	}

	public static boolean isActive() {
		return active;
	}

	public static String intervalName() {
		return INTERVAL_NAMES[interval];
	}

	public static String rewardName() {
		return REWARD_NAMES[reward];
	}

	public static void cycleInterval() {
		interval = (interval + 1) % INTERVAL_TICKS.length;
	}

	public static void cycleReward() {
		reward = (reward + 1) % REWARD_ITEMS.length;
	}

	public static void start(MinecraftServer server, String actor) {
		active = true;
		counter = 0;
		StaffLog.record(actor, "event gestart: Raining Tacos (" + intervalName() + ", " + rewardName() + ")");
		Cmd.run(server, "tellraw @a {text:\"Raining Tacos is gestart! Pak de tacos!\",color:\"gold\"}");
	}

	public static void stop(MinecraftServer server, String actor) {
		active = false;
		StaffLog.record(actor, "event gestopt: Raining Tacos");
		Cmd.run(server, "tellraw @a {text:\"Raining Tacos is afgelopen.\",color:\"gold\"}");
	}

	/** Wordt elke server-tick aangeroepen. */
	public static void tick(MinecraftServer server) {
		if (!active) {
			return;
		}
		counter++;
		if (counter < INTERVAL_TICKS[interval]) {
			return;
		}
		counter = 0;
		Cmd.runQuiet(server, "execute at @a run summon minecraft:item ~ ~8 ~ {Item:"
				+ REWARD_ITEMS[reward] + ",Motion:[0.0d,-0.1d,0.0d]}");
	}
}
