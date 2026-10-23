package com.example.banhammer;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanhammerMod implements ModInitializer {
	public static final String MOD_ID = "banhammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// /adminpanel  en  /adminpanel speler <naam>  (alleen voor Staff of hoger)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("adminpanel")
						.requires(source -> source.getPlayer() != null && Roles.has(source.getPlayer(), Role.STAFF))
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							if (!Roles.has(player, Role.STAFF)) {
								return 0;
							}
							AdminPanel.openMain(player);
							return 1;
						})
						.then(Commands.literal("speler")
								.then(Commands.argument("naam", StringArgumentType.word())
										.executes(context -> {
											ServerPlayer player = context.getSource().getPlayerOrException();
											if (!Roles.has(player, Role.STAFF)) {
												return 0;
											}
											String name = StringArgumentType.getString(context, "naam");
											ServerPlayer target = player.level().getServer().getPlayerList().getPlayerByName(name);
											if (target == null) {
												player.sendSystemMessage(Component.literal("Admin Panel: " + name + " is niet online."));
												return 0;
											}
											AdminPanel.openTarget(player, target.getUUID());
											return 1;
										})))));

		// Event-timer (Raining Tacos)
		ServerTickEvents.END_SERVER_TICK.register(Events::tick);

		// Shift + rechtsklik met een hamer: volgende reden / andere stand
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			boolean ban = Hammers.isHammer(held, true);
			boolean kick = Hammers.isHammer(held, false);
			boolean op = Hammers.isOpHammer(held);
			if (!ban && !kick && !op) {
				return InteractionResult.PASS;
			}
			Role needed = op ? Role.OWNER : (ban ? Role.ADMIN : Role.MODERATOR);
			if (!Roles.has(sp, needed)) {
				return InteractionResult.PASS;
			}

			if (op) {
				Hammers.cycleOp(held);
				sp.sendSystemMessage(Component.literal("Ophammer: " + Hammers.opModeText(held)), true);
			} else {
				Hammers.cycle(held, ban);
				sp.sendSystemMessage(Component.literal("Reden: " + Hammers.reason(held, ban)), true);
			}
			return InteractionResult.SUCCESS;
		});

		// Slaan met een hamer of met het custom zwaard
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}

			ItemStack held = attacker.getItemInHand(hand);
			MinecraftServer server = attacker.level().getServer();

			// Custom zwaard: effect bij slag, de schade zelf gaat gewoon door
			CustomData data = held.get(DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().contains("bhsword")) {
				CustomItems.onHit(server, entity, data.copyTag().getStringOr("effect", "none"));
				return InteractionResult.PASS;
			}

			boolean ban = Hammers.isHammer(held, true);
			boolean kick = Hammers.isHammer(held, false);
			boolean op = Hammers.isOpHammer(held);
			if (!ban && !kick && !op) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}

			if (op) {
				return useOpHammer(attacker, target, held, server);
			}

			String attackerName = attacker.getGameProfile().name();
			String targetName = target.getGameProfile().name();

			Role needed = ban ? Role.ADMIN : Role.MODERATOR;
			if (!Roles.has(attacker, needed)) {
				attacker.sendSystemMessage(Component.literal("Hamer: je hebt daar geen rechten voor (minimaal " + needed.label + ")."));
				return InteractionResult.SUCCESS;
			}
			if (Roles.isProtected(target)) {
				attacker.sendSystemMessage(Component.literal(
						"Hamer: " + targetName + " is staff/operator en kan niet worden " + (ban ? "gebanned." : "gekicked.")));
				return InteractionResult.SUCCESS;
			}

			String reason = Hammers.reason(held, ban);
			String command = ban
					? "ban " + targetName + " " + reason
					: "kick " + targetName + " " + reason;
			StaffLog.record(attackerName, command);
			Cmd.run(server, command);

			attacker.sendSystemMessage(Component.literal(
					"Hamer: " + targetName + (ban ? " is gebanned" : " is gekicked") + " (" + reason + ")."));
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Banhammer, Kickhammer, Ophammer, rollen en Admin Panel (server-only) geladen!");
	}

	private static InteractionResult useOpHammer(ServerPlayer attacker, ServerPlayer target, ItemStack held, MinecraftServer server) {
		String attackerName = attacker.getGameProfile().name();
		String targetName = target.getGameProfile().name();

		if (!Roles.has(attacker, Role.OWNER)) {
			attacker.sendSystemMessage(Component.literal("Ophammer: alleen owners mogen deze hamer gebruiken."));
			return InteractionResult.SUCCESS;
		}

		boolean targetIsOp = Ops.isOperatorStrict(target);
		String command;
		String done;

		if (Hammers.givesOp(held)) {
			if (targetIsOp) {
				attacker.sendSystemMessage(Component.literal("Ophammer: " + targetName + " is al operator."));
				return InteractionResult.SUCCESS;
			}
			command = "op " + targetName;
			done = targetName + " is nu operator.";
		} else {
			if (!targetIsOp) {
				attacker.sendSystemMessage(Component.literal("Ophammer: " + targetName + " is geen operator."));
				return InteractionResult.SUCCESS;
			}
			if (Roles.of(target) == Role.OWNER) {
				attacker.sendSystemMessage(Component.literal("Ophammer: " + targetName + " is owner en kan niet worden gedeopt."));
				return InteractionResult.SUCCESS;
			}
			command = "deop " + targetName;
			done = targetName + " is geen operator meer.";
		}

		StaffLog.record(attackerName, command);
		Cmd.run(server, command);
		attacker.sendSystemMessage(Component.literal("Ophammer: " + done));
		return InteractionResult.SUCCESS;
	}
}
