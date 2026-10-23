package com.example.banhammer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Server-side kistmenu (werkt voor gewone clients zonder mod).
 * Elke knop heeft een minimale rol; die wordt bij het tekenen EN bij het klikken server-side gecontroleerd.
 */
public final class AdminPanel {
	private static final int LIST_SLOTS = 45;
	private static final String[] RANDOM_EFFECTS = {"speed", "jump_boost", "glowing", "slow_falling", "haste", "night_vision"};

	private AdminPanel() {
	}

	private interface Action {
		/** Geeft true terug als het menu is gesloten of vervangen. */
		boolean run(ServerPlayer clicker);
	}

	private record Button(ItemStack stack, Action action, Role min) {
	}

	// ---------- Hulpfuncties ----------

	private static ItemStack named(Item item, String name, ChatFormatting color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false)));
		return stack;
	}

	private static String nameOf(ServerPlayer player) {
		return player.getGameProfile().name();
	}

	private static void tell(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal("Admin Panel: " + text));
	}

	/** Voert een commando uit met serverrechten en logt het. */
	private static void exec(ServerPlayer actor, String command) {
		StaffLog.record(nameOf(actor), command);
		Cmd.run(actor.level().getServer(), command);
	}

	private static Action giveAction(Supplier<ItemStack> factory) {
		return clicker -> {
			if (!clicker.getInventory().add(factory.get())) {
				tell(clicker, "je inventaris is vol.");
			}
			return false;
		};
	}

	private static void punish(ServerPlayer clicker, UUID targetId, boolean ban) {
		ServerPlayer target = clicker.level().getServer().getPlayerList().getPlayer(targetId);
		if (target == null) {
			tell(clicker, "die speler is niet meer online.");
			return;
		}
		String name = nameOf(target);
		if (Roles.isProtected(target)) {
			tell(clicker, name + " is staff/operator en is beschermd.");
			return;
		}
		exec(clicker, ban
				? "ban " + name + " Gebanned via het Admin Panel"
				: "kick " + name + " Gekicked via het Admin Panel");
		tell(clicker, name + (ban ? " is gebanned." : " is gekicked."));
	}

	private static void sendTo(ServerPlayer actor, String playerName, String key) {
		String command = Spawns.tpCommand(playerName, key);
		if (command == null) {
			tell(actor, "dat punt is nog niet ingesteld (pagina 5).");
			return;
		}
		exec(actor, command);
	}

	private static void setPoint(ServerPlayer actor, String key) {
		Spawns.set(key, new Spawns.Point(actor.getX(), actor.getY(), actor.getZ()));
		StaffLog.record(nameOf(actor), "punt '" + key + "' ingesteld op jouw positie");
		tell(actor, "punt '" + key + "' ingesteld (teleporteren gebeurt naar de Overworld).");
	}

	private static void applyTemplate(ServerPlayer actor, String templateName, UUID targetId) {
		MinecraftServer server = actor.level().getServer();
		ServerPlayer target = server.getPlayerList().getPlayer(targetId);
		if (target == null) {
			tell(actor, "die speler is niet meer online.");
			return;
		}
		String targetName = nameOf(target);
		List<String> commands = Templates.commands(templateName, targetName);
		if (commands.isEmpty()) {
			tell(actor, "template bestaat niet meer.");
			return;
		}
		for (String command : commands) {
			Cmd.run(server, command);
		}
		StaffLog.record(nameOf(actor), "template " + templateName + " toegepast op " + targetName);
		tell(actor, "template " + templateName + " toegepast op " + targetName + ".");
	}

	// ---------- Pagina-bouwer ----------

	private static final class Page {
		private final ServerPlayer viewer;
		private final Map<Integer, Button> buttons = new HashMap<>();

		Page(ServerPlayer viewer) {
			this.viewer = viewer;
		}

		/** Voegt de knop alleen toe als de kijker de minimale rol heeft. */
		Page add(int slot, Role min, ItemStack stack, Action action) {
			if (Roles.has(viewer, min)) {
				buttons.put(slot, new Button(stack, action, min));
			}
			return this;
		}

		Page info(int slot, ItemStack stack) {
			return add(slot, Role.STAFF, stack, p -> false);
		}

		Page cmd(int slot, Role min, Item item, String name, ChatFormatting color, String... commands) {
			return add(slot, min, named(item, name, color), p -> {
				for (String command : commands) {
					exec(p, command);
				}
				return false;
			});
		}

		Page go(int slot, Role min, Item item, String name, ChatFormatting color, Consumer<ServerPlayer> target) {
			return add(slot, min, named(item, name, color), p -> {
				target.accept(p);
				return true;
			});
		}

		Page back(int slot, Consumer<ServerPlayer> target) {
			return go(slot, Role.STAFF, Items.ARROW, "Terug", ChatFormatting.GRAY, target);
		}

		void open(String title, int rows) {
			SimpleContainer container = new SimpleContainer(rows * 9);
			for (Map.Entry<Integer, Button> entry : buttons.entrySet()) {
				container.setItem(entry.getKey(), entry.getValue().stack().copy());
			}
			Map<Integer, Button> snapshot = buttons;
			viewer.openMenu(new SimpleMenuProvider(
					(syncId, inventory, player) -> new PanelMenu(syncId, inventory, container, rows, snapshot),
					Component.literal(title)));
		}
	}

	// ---------- Hoofdmenu ----------

	public static void openMain(ServerPlayer v) {
		new Page(v)
				.go(9, Role.STAFF, Items.COMPASS, "1. Dashboard", ChatFormatting.AQUA, AdminPanel::openDashboard)
				.go(10, Role.STAFF, Items.PLAYER_HEAD, "2. Spelers", ChatFormatting.WHITE, AdminPanel::openPlayers)
				.go(11, Role.ADMIN, Items.GOLDEN_APPLE, "3. Gamemode", ChatFormatting.GREEN, AdminPanel::openGamemode)
				.go(12, Role.ADMIN, Items.FIREWORK_ROCKET, "4. Fun", ChatFormatting.LIGHT_PURPLE, AdminPanel::openFun)
				.go(13, Role.MODERATOR, Items.ENDER_PEARL, "5. Spawn / Return", ChatFormatting.YELLOW, AdminPanel::openSpawn)
				.go(14, Role.STAFF, Items.CAKE, "6. Events", ChatFormatting.GOLD, AdminPanel::openEvents)
				.go(15, Role.ADMIN, Items.DIAMOND_SWORD, "7. Custom Items", ChatFormatting.AQUA, AdminPanel::openCustomItems)
				.go(16, Role.ADMIN, Items.CHEST, "8. Inventaris-templates", ChatFormatting.WHITE, AdminPanel::openTemplates)
				.go(17, Role.OWNER, Items.NETHER_STAR, "9. Rollen", ChatFormatting.LIGHT_PURPLE, AdminPanel::openRoles)
				.go(29, Role.MODERATOR, Items.MACE, "Hamers", ChatFormatting.RED, AdminPanel::openHammers)
				.add(31, Role.STAFF, named(Items.BARRIER, "Sluiten", ChatFormatting.RED), p -> {
					p.closeContainer();
					return true;
				})
				.open("Admin Panel (" + Roles.of(v).label + ")", 4);
	}

	// ---------- 1. Dashboard ----------

	public static void openDashboard(ServerPlayer v) {
		int online = v.level().getServer().getPlayerList().getPlayers().size();
		boolean event = Events.isActive();
		new Page(v)
				.info(10, named(Items.PLAYER_HEAD, "Online spelers: " + online, ChatFormatting.WHITE))
				.info(11, named(Items.CAKE, "Actief event: " + (event ? "Raining Tacos" : "geen"), ChatFormatting.GOLD))
				.info(12, named(Items.CLOCK, "Server-modus: " + (event ? "event" : "normaal"), ChatFormatting.YELLOW))
				.info(13, named(Items.NAME_TAG, "Jouw rol: " + Roles.of(v).label, ChatFormatting.GREEN))
				.go(15, Role.MODERATOR, Items.WRITABLE_BOOK, "Logs bekijken", ChatFormatting.AQUA, AdminPanel::openLogs)
				.back(22, AdminPanel::openMain)
				.open("1. Dashboard", 3);
	}

	public static void openLogs(ServerPlayer v) {
		Page page = new Page(v);
		List<String> lines = StaffLog.last(LIST_SLOTS);
		int slot = 0;
		for (int i = lines.size() - 1; i >= 0 && slot < LIST_SLOTS; i--) {
			String line = lines.get(i);
			if (line.length() > 90) {
				line = line.substring(0, 90);
			}
			page.add(slot++, Role.MODERATOR, named(Items.PAPER, line, ChatFormatting.WHITE), p -> false);
		}
		page.back(49, AdminPanel::openDashboard).open("Logs (nieuwste eerst)", 6);
	}

	// ---------- 2. Spelers ----------

	public static void openPlayers(ServerPlayer v) {
		Page page = new Page(v);
		int slot = 0;
		for (ServerPlayer target : v.level().getServer().getPlayerList().getPlayers()) {
			if (slot >= LIST_SLOTS) {
				break;
			}
			if (Roles.isProtected(target)) {
				continue; // staff en operators staan niet in de lijst
			}
			UUID id = target.getUUID();
			page.go(slot++, Role.STAFF, Items.NAME_TAG, nameOf(target), ChatFormatting.WHITE, p -> openTarget(p, id));
		}
		page.back(49, AdminPanel::openMain).open("2. Spelers", 6);
	}

	public static void openTarget(ServerPlayer v, UUID id) {
		ServerPlayer t = v.level().getServer().getPlayerList().getPlayer(id);
		if (t == null) {
			openPlayers(v);
			return;
		}
		String name = nameOf(t);
		String self = nameOf(v);

		new Page(v)
				.info(4, named(Items.NAME_TAG, name + (Roles.isProtected(t) ? " (staff)" : ""), ChatFormatting.WHITE))

				.cmd(10, Role.STAFF, Items.ENDER_PEARL, "Teleporteer naar " + name, ChatFormatting.LIGHT_PURPLE,
						"tp " + self + " " + name)
				.cmd(11, Role.ADMIN, Items.ENDER_EYE, "Haal " + name + " naar jou", ChatFormatting.DARK_PURPLE,
						"tp " + name + " " + self)
				.cmd(12, Role.MODERATOR, Items.COPPER_BLOCK, "Waarschuw met bliksem", ChatFormatting.YELLOW,
						"title " + name + " title {text:\"WAARSCHUWING\",color:\"red\",bold:true}",
						"title " + name + " subtitle {text:\"Let op je gedrag!\",color:\"yellow\"}",
						"execute at " + name + " run summon minecraft:lightning_bolt ~6 ~ ~")
				.add(13, Role.MODERATOR, named(Items.IRON_BOOTS, "Kick " + name, ChatFormatting.GOLD), p -> {
					punish(p, id, false);
					p.closeContainer();
					return true;
				})
				.add(14, Role.ADMIN, named(Items.TNT, "Ban " + name, ChatFormatting.RED), p -> {
					punish(p, id, true);
					p.closeContainer();
					return true;
				})
				.cmd(15, Role.ADMIN, Items.COPPER_BLOCK, "Bliksem recht op " + name, ChatFormatting.DARK_RED,
						"execute at " + name + " run summon minecraft:lightning_bolt ~ ~ ~")

				.cmd(19, Role.ADMIN, Items.IRON_SWORD, "Survival voor " + name, ChatFormatting.GRAY, "gamemode survival " + name)
				.cmd(20, Role.ADMIN, Items.GRASS_BLOCK, "Creative voor " + name, ChatFormatting.GREEN, "gamemode creative " + name)
				.cmd(21, Role.ADMIN, Items.WOODEN_PICKAXE, "Adventure voor " + name, ChatFormatting.YELLOW, "gamemode adventure " + name)
				.cmd(22, Role.ADMIN, Items.GLASS, "Special (spectator) voor " + name, ChatFormatting.AQUA, "gamemode spectator " + name)
				.cmd(24, Role.ADMIN, Items.GLISTERING_MELON_SLICE, "Genees " + name, ChatFormatting.RED,
						"effect give " + name + " minecraft:instant_health 1 10 true")
				.cmd(25, Role.ADMIN, Items.COOKED_BEEF, "Voed " + name, ChatFormatting.GOLD,
						"effect give " + name + " minecraft:saturation 1 20 true")

				.add(28, Role.ADMIN, named(Items.SPYGLASS, "Inventaris bekijken", ChatFormatting.AQUA), p -> {
					StaffLog.record(nameOf(p), "bekeek inventaris van " + name);
					openInventoryView(p, id);
					return true;
				})
				.go(29, Role.ADMIN, Items.CHEST, "Inventaris-template toepassen", ChatFormatting.WHITE, p -> openTemplatePicker(p, id))
				.add(31, Role.MODERATOR, named(Items.RED_WOOL, "Stuur naar spawn", ChatFormatting.GREEN), p -> {
					sendTo(p, name, "spawn");
					return false;
				})
				.add(32, Role.MODERATOR, named(Items.IRON_BARS, "Stuur naar respawn-kamer", ChatFormatting.GRAY), p -> {
					sendTo(p, name, "room");
					return false;
				})

				.back(40, AdminPanel::openPlayers)
				.open("Speler: " + name, 5);
	}

	public static void openInventoryView(ServerPlayer v, UUID id) {
		ServerPlayer t = v.level().getServer().getPlayerList().getPlayer(id);
		if (t == null) {
			openPlayers(v);
			return;
		}
		Page page = new Page(v);
		Inventory inventory = t.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && i < LIST_SLOTS; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				page.add(i, Role.ADMIN, stack.copy(), p -> false);
			}
		}
		page.back(49, p -> openTarget(p, id)).open("Inventaris van " + nameOf(t), 6);
	}

	public static void openTemplatePicker(ServerPlayer v, UUID id) {
		Page page = new Page(v);
		int slot = 0;
		for (Map.Entry<String, List<Templates.Entry>> entry : Templates.load().entrySet()) {
			if (slot >= LIST_SLOTS) {
				break;
			}
			String templateName = entry.getKey();
			page.add(slot++, Role.ADMIN,
					named(Items.CHEST, templateName + " (" + entry.getValue().size() + " items)", ChatFormatting.WHITE), p -> {
						applyTemplate(p, templateName, id);
						p.closeContainer();
						return true;
					});
		}
		page.back(49, p -> openTarget(p, id)).open("Kies een template", 6);
	}

	// ---------- 3. Gamemode ----------

	public static void openGamemode(ServerPlayer v) {
		String n = nameOf(v);
		new Page(v)
				.cmd(10, Role.ADMIN, Items.IRON_SWORD, "Survival", ChatFormatting.GRAY, "gamemode survival " + n)
				.cmd(11, Role.ADMIN, Items.GRASS_BLOCK, "Creative", ChatFormatting.GREEN, "gamemode creative " + n)
				.cmd(12, Role.ADMIN, Items.WOODEN_PICKAXE, "Adventure", ChatFormatting.YELLOW, "gamemode adventure " + n)
				.cmd(13, Role.ADMIN, Items.GLASS, "Special (spectator)", ChatFormatting.AQUA, "gamemode spectator " + n)
				.cmd(15, Role.ADMIN, Items.GLISTERING_MELON_SLICE, "Genees jezelf", ChatFormatting.RED,
						"effect give " + n + " minecraft:instant_health 1 10 true")
				.cmd(16, Role.ADMIN, Items.COOKED_BEEF, "Voed jezelf", ChatFormatting.GOLD,
						"effect give " + n + " minecraft:saturation 1 20 true")
				.cmd(19, Role.ADMIN, Items.POTION, "Onzichtbaar", ChatFormatting.LIGHT_PURPLE,
						"effect give " + n + " minecraft:invisibility infinite 0 true")
				.cmd(20, Role.ADMIN, Items.GOLDEN_CARROT, "Nachtzicht", ChatFormatting.YELLOW,
						"effect give " + n + " minecraft:night_vision infinite 0 true")
				.cmd(21, Role.ADMIN, Items.MILK_BUCKET, "Alle effecten wissen", ChatFormatting.WHITE, "effect clear " + n)
				.back(26, AdminPanel::openMain)
				.open("3. Gamemode", 3);
	}

	// ---------- 4. Fun ----------

	public static void openFun(ServerPlayer v) {
		String n = nameOf(v);
		new Page(v)
				.cmd(10, Role.ADMIN, Items.SUNFLOWER, "Dag", ChatFormatting.YELLOW, "time set day")
				.cmd(11, Role.ADMIN, Items.COAL, "Nacht", ChatFormatting.DARK_GRAY, "time set night")
				.cmd(12, Role.ADMIN, Items.FEATHER, "Helder weer", ChatFormatting.WHITE, "weather clear")
				.cmd(13, Role.ADMIN, Items.WATER_BUCKET, "Regen", ChatFormatting.BLUE, "weather rain")
				.cmd(14, Role.ADMIN, Items.COPPER_BLOCK, "Onweer", ChatFormatting.DARK_PURPLE, "weather thunder")
				.cmd(15, Role.ADMIN, Items.POPPY, "Moeilijkheid: peaceful", ChatFormatting.GREEN, "difficulty peaceful")
				.cmd(16, Role.ADMIN, Items.IRON_SWORD, "Moeilijkheid: normal", ChatFormatting.YELLOW, "difficulty normal")
				.cmd(17, Role.ADMIN, Items.ZOMBIE_HEAD, "Moeilijkheid: hard", ChatFormatting.RED, "difficulty hard")

				.add(19, Role.ADMIN, named(Items.SPLASH_POTION, "Willekeurig effect voor iedereen", ChatFormatting.LIGHT_PURPLE), p -> {
					String effect = RANDOM_EFFECTS[ThreadLocalRandom.current().nextInt(RANDOM_EFFECTS.length)];
					exec(p, "effect give @a minecraft:" + effect + " 20 0 true");
					tell(p, "iedereen kreeg: " + effect);
					return false;
				})
				.cmd(20, Role.ADMIN, Items.CHEST, "Loot voor iedereen", ChatFormatting.GOLD,
						"loot give @a loot minecraft:chests/simple_dungeon")
				.cmd(21, Role.ADMIN, Items.GLOWSTONE_DUST, "Gloed aan (jezelf)", ChatFormatting.YELLOW,
						"effect give " + n + " minecraft:glowing infinite 0 true")
				.cmd(22, Role.ADMIN, Items.GUNPOWDER, "Gloed uit (jezelf)", ChatFormatting.GRAY,
						"effect clear " + n + " minecraft:glowing")
				.cmd(23, Role.ADMIN, Items.FIREWORK_ROCKET, "Vuurwerk-deeltjes", ChatFormatting.LIGHT_PURPLE,
						"execute at " + n + " run particle minecraft:firework ~ ~1 ~ 0.5 0.5 0.5 0.1 80")
				.cmd(24, Role.ADMIN, Items.HOPPER, "Verwijder gedropte items", ChatFormatting.GOLD, "kill @e[type=item]")
				.back(26, AdminPanel::openMain)
				.open("4. Fun", 3);
	}

	// ---------- 5. Spawn / Return ----------

	public static void openSpawn(ServerPlayer v) {
		String self = nameOf(v);
		new Page(v)
				.add(10, Role.MODERATOR, named(Items.RED_WOOL, "Teleporteer mezelf naar spawn", ChatFormatting.GREEN), p -> {
					sendTo(p, self, "spawn");
					return false;
				})
				.add(11, Role.MODERATOR, named(Items.IRON_BARS, "Teleporteer mezelf naar respawn-kamer", ChatFormatting.GRAY), p -> {
					sendTo(p, self, "room");
					return false;
				})
				.add(13, Role.ADMIN, named(Items.LODESTONE, "Stel spawn in op mijn positie", ChatFormatting.YELLOW), p -> {
					setPoint(p, "spawn");
					return false;
				})
				.add(14, Role.ADMIN, named(Items.IRON_DOOR, "Stel respawn-kamer in op mijn positie", ChatFormatting.YELLOW), p -> {
					setPoint(p, "room");
					return false;
				})
				.add(16, Role.ADMIN, named(Items.ENDER_EYE, "Stuur iedereen naar spawn", ChatFormatting.RED), p -> {
					String command = Spawns.tpCommand("@a", "spawn");
					if (command == null) {
						tell(p, "de spawn is nog niet ingesteld.");
					} else {
						exec(p, command);
					}
					return false;
				})
				.back(22, AdminPanel::openMain)
				.open("5. Spawn / Return", 3);
	}

	// ---------- 6. Events ----------

	public static void openEvents(ServerPlayer v) {
		boolean active = Events.isActive();
		new Page(v)
				.info(10, named(Items.CAKE, "Raining Tacos: " + (active ? "ACTIEF" : "uit"),
						active ? ChatFormatting.GREEN : ChatFormatting.RED))
				.add(12, Role.ADMIN, named(Items.LIME_WOOL, "Start Raining Tacos", ChatFormatting.GREEN), p -> {
					Events.start(p.level().getServer(), nameOf(p));
					openEvents(p);
					return true;
				})
				.add(13, Role.ADMIN, named(Items.RED_WOOL, "Stop Raining Tacos", ChatFormatting.RED), p -> {
					Events.stop(p.level().getServer(), nameOf(p));
					openEvents(p);
					return true;
				})
				.add(15, Role.ADMIN, named(Items.CLOCK, "Snelheid: " + Events.intervalName() + " (klik om te wisselen)", ChatFormatting.YELLOW), p -> {
					Events.cycleInterval();
					StaffLog.record(nameOf(p), "event-snelheid: " + Events.intervalName());
					openEvents(p);
					return true;
				})
				.add(16, Role.ADMIN, named(Items.CHEST, "Beloning: " + Events.rewardName() + " (klik om te wisselen)", ChatFormatting.GOLD), p -> {
					Events.cycleReward();
					StaffLog.record(nameOf(p), "event-beloning: " + Events.rewardName());
					openEvents(p);
					return true;
				})
				.back(22, AdminPanel::openMain)
				.open("6. Events", 3);
	}

	// ---------- 7. Custom Items ----------

	public static void openCustomItems(ServerPlayer v) {
		int[] c = CustomItems.config(v.getUUID());
		new Page(v)
				.add(10, Role.ADMIN, named(Items.IRON_INGOT, "Materiaal: " + CustomItems.MATERIALS[c[0]] + " (klik)", ChatFormatting.GRAY), p -> {
					CustomItems.cycle(p.getUUID(), 0);
					openCustomItems(p);
					return true;
				})
				.add(11, Role.ADMIN, named(Items.REDSTONE, "Schade: " + CustomItems.DAMAGES[c[1]] + " (klik)", ChatFormatting.RED), p -> {
					CustomItems.cycle(p.getUUID(), 1);
					openCustomItems(p);
					return true;
				})
				.add(12, Role.ADMIN, named(Items.BLAZE_POWDER, "Effect bij slag: " + CustomItems.EFFECT_NAMES[c[2]] + " (klik)", ChatFormatting.GOLD), p -> {
					CustomItems.cycle(p.getUUID(), 2);
					openCustomItems(p);
					return true;
				})
				.add(13, Role.ADMIN, named(Items.WRITABLE_BOOK, "Lore: " + CustomItems.LORES[c[3]] + " (klik)", ChatFormatting.AQUA), p -> {
					CustomItems.cycle(p.getUUID(), 3);
					openCustomItems(p);
					return true;
				})
				.add(15, Role.ADMIN, named(Items.DIAMOND_SWORD, "Geef dit zwaard", ChatFormatting.GREEN), p -> {
					exec(p, CustomItems.giveCommand(nameOf(p), CustomItems.config(p.getUUID())));
					return false;
				})
				.back(22, AdminPanel::openMain)
				.open("7. Custom Items", 3);
	}

	// ---------- 8. Inventaris-templates ----------

	public static void openTemplates(ServerPlayer v) {
		Page page = new Page(v);
		int slot = 0;
		for (Map.Entry<String, List<Templates.Entry>> entry : Templates.load().entrySet()) {
			if (slot >= LIST_SLOTS) {
				break;
			}
			String templateName = entry.getKey();
			page.go(slot++, Role.ADMIN, Items.CHEST, templateName + " (" + entry.getValue().size() + " items)",
					ChatFormatting.WHITE, p -> openTemplateDetail(p, templateName));
		}
		page.add(47, Role.ADMIN, named(Items.ANVIL, "Sla mijn inventaris op als template", ChatFormatting.GREEN), p -> {
			String saved = Templates.saveFrom(p);
			StaffLog.record(nameOf(p), "template opgeslagen: " + saved);
			tell(p, "opgeslagen als " + saved);
			openTemplates(p);
			return true;
		});
		page.back(49, AdminPanel::openMain).open("8. Inventaris-templates", 6);
	}

	public static void openTemplateDetail(ServerPlayer v, String templateName) {
		new Page(v)
				.info(4, named(Items.CHEST, templateName, ChatFormatting.WHITE))
				.add(11, Role.ADMIN, named(Items.LIME_WOOL, "Pas toe op mezelf", ChatFormatting.GREEN), p -> {
					applyTemplate(p, templateName, p.getUUID());
					return false;
				})
				.add(15, Role.ADMIN, named(Items.RED_WOOL, "Verwijder template", ChatFormatting.RED), p -> {
					Templates.delete(templateName);
					StaffLog.record(nameOf(p), "template verwijderd: " + templateName);
					openTemplates(p);
					return true;
				})
				.back(22, AdminPanel::openTemplates)
				.open("Template: " + templateName, 3);
	}

	// ---------- 9. Rollen (alleen Owner) ----------

	public static void openRoles(ServerPlayer v) {
		Page page = new Page(v);
		int slot = 0;
		for (ServerPlayer target : v.level().getServer().getPlayerList().getPlayers()) {
			if (slot >= LIST_SLOTS) {
				break;
			}
			UUID id = target.getUUID();
			page.go(slot++, Role.OWNER, Items.NAME_TAG, nameOf(target) + " [" + Roles.of(target).label + "]",
					ChatFormatting.WHITE, p -> openRolePicker(p, id));
		}
		page.back(49, AdminPanel::openMain).open("9. Rollen", 6);
	}

	public static void openRolePicker(ServerPlayer v, UUID id) {
		ServerPlayer t = v.level().getServer().getPlayerList().getPlayer(id);
		if (t == null) {
			openRoles(v);
			return;
		}
		String name = nameOf(t);
		new Page(v)
				.info(4, named(Items.NAME_TAG, name + " [" + Roles.of(t).label + "]", ChatFormatting.WHITE))
				.add(10, Role.OWNER, named(Items.COPPER_INGOT, "Maak Staff", ChatFormatting.GRAY), p -> setRole(p, name, id, Role.STAFF))
				.add(11, Role.OWNER, named(Items.IRON_INGOT, "Maak Moderator", ChatFormatting.WHITE), p -> setRole(p, name, id, Role.MODERATOR))
				.add(12, Role.OWNER, named(Items.GOLD_INGOT, "Maak Admin", ChatFormatting.GOLD), p -> setRole(p, name, id, Role.ADMIN))
				.add(14, Role.OWNER, named(Items.BARRIER, "Geen rol (ook niet via ops)", ChatFormatting.RED), p -> setRole(p, name, id, Role.NONE))
				.add(15, Role.OWNER, named(Items.ARROW, "Standaard (operator = Admin)", ChatFormatting.YELLOW), p -> {
					Roles.reset(name);
					StaffLog.record(nameOf(p), "rol van " + name + " teruggezet naar standaard");
					openRolePicker(p, id);
					return true;
				})
				.back(22, AdminPanel::openRoles)
				.open("Rol van " + name, 3);
	}

	private static boolean setRole(ServerPlayer actor, String name, UUID id, Role role) {
		Roles.set(name, role);
		StaffLog.record(nameOf(actor), "rol van " + name + " gezet op " + role.label);
		openRolePicker(actor, id);
		return true;
	}

	// ---------- Hamers ----------

	public static void openHammers(ServerPlayer v) {
		new Page(v)
				.add(11, Role.ADMIN, Hammers.banhammer(), giveAction(Hammers::banhammer))
				.add(13, Role.OWNER, Hammers.ophammer(), giveAction(Hammers::ophammer))
				.add(15, Role.MODERATOR, Hammers.kickhammer(), giveAction(Hammers::kickhammer))
				.back(22, AdminPanel::openMain)
				.open("Hamers (Shift + rechtsklik = andere reden)", 3);
	}

	// ---------- Het menu zelf ----------

	private static MenuType<?> menuType(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	private static final class PanelMenu extends ChestMenu {
		private final int rows;
		private final Map<Integer, Button> buttons;

		PanelMenu(int syncId, Inventory inventory, Container container, int rows, Map<Integer, Button> buttons) {
			super(menuType(rows), syncId, inventory, container, rows);
			this.rows = rows;
			this.buttons = buttons;
		}

		/** Niets mag uit het menu worden gehaald; elke klik wordt hier afgehandeld en de rol opnieuw gecontroleerd. */
		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			if (player instanceof ServerPlayer clicker && slotId >= 0 && slotId < rows * 9) {
				if (!Roles.has(clicker, Role.STAFF)) {
					clicker.closeContainer();
					return;
				}
				Button clickedButton = buttons.get(slotId);
				if (clickedButton != null) {
					if (!Roles.has(clicker, clickedButton.min())) {
						tell(clicker, "daar heb je geen rechten voor.");
					} else if (clickedButton.action().run(clicker)) {
						return; // menu is gesloten of vervangen
					}
				}
			}
			sendAllDataToRemote(); // zet het scherm van de speler weer recht
		}
	}
}
