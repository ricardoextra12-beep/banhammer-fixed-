package com.example.banhammer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Bouwt de twee hamers. Beide zijn een mace; alleen de banhammer heeft een enchant-glans.
 * Met Shift + rechtermuisknop wissel je van reden. De gekozen reden staat in de naam.
 * Wil je andere redenen? Pas de twee lijsten hieronder aan.
 */
public final class Hammers {
	public static final List<String> BAN_REASONS = List.of(
			"Cheaten of hacken",
			"Griefen",
			"Spammen",
			"Beledigen of pesten",
			"Reclame maken",
			"Regels overtreden",
			"Bug misbruiken",
			"Gebanned door staff"
	);

	public static final List<String> KICK_REASONS = List.of(
			"AFK",
			"Spammen",
			"Taalgebruik",
			"Storen",
			"Waarschuwing",
			"Even afkoelen"
	);

	private Hammers() {
	}

	public static ItemStack banhammer() {
		ItemStack stack = new ItemStack(Items.MACE);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		apply(stack, true, 0);
		return stack;
	}

	public static ItemStack kickhammer() {
		ItemStack stack = new ItemStack(Items.MACE);
		apply(stack, false, 0);
		return stack;
	}

	public static boolean isHammer(ItemStack stack, boolean ban) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(ban ? "banhammer" : "kickhammer");
	}

	public static int reasonIndex(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return 0;
		}
		return data.copyTag().getIntOr("reason", 0);
	}

	public static String reason(ItemStack stack, boolean ban) {
		List<String> reasons = ban ? BAN_REASONS : KICK_REASONS;
		return reasons.get(Math.floorMod(reasonIndex(stack), reasons.size()));
	}

	/** Zet de volgende reden op de hamer (en past de naam aan). */
	public static void cycle(ItemStack stack, boolean ban) {
		apply(stack, ban, reasonIndex(stack) + 1);
	}

	// ---------- Ophammer: OP geven / OP weghalen ----------

	public static ItemStack ophammer() {
		ItemStack stack = new ItemStack(Items.MACE);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		applyOp(stack, 0);
		return stack;
	}

	public static boolean isOpHammer(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains("ophammer");
	}

	/** 0 = OP geven, 1 = OP weghalen */
	public static int opMode(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return 0;
		}
		return data.copyTag().getIntOr("opmode", 0);
	}

	public static boolean givesOp(ItemStack stack) {
		return opMode(stack) == 0;
	}

	public static String opModeText(ItemStack stack) {
		return givesOp(stack) ? "OP geven" : "OP weghalen";
	}

	public static void cycleOp(ItemStack stack) {
		applyOp(stack, givesOp(stack) ? 1 : 0);
	}

	private static void applyOp(ItemStack stack, int mode) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("ophammer", true);
		tag.putInt("opmode", mode);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		String text = mode == 0 ? "OP geven" : "OP weghalen";
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal("Ophammer")
						.withStyle(style -> style.withColor(ChatFormatting.GREEN).withItalic(false))
						.append(Component.literal(" - " + text)
								.withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))));
	}

	private static void apply(ItemStack stack, boolean ban, int index) {
		List<String> reasons = ban ? BAN_REASONS : KICK_REASONS;
		int i = Math.floorMod(index, reasons.size());
		ChatFormatting color = ban ? ChatFormatting.RED : ChatFormatting.GOLD;

		CompoundTag tag = new CompoundTag();
		tag.putBoolean(ban ? "banhammer" : "kickhammer", true);
		tag.putInt("reason", i);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(ban ? "Banhammer" : "Kickhammer")
						.withStyle(style -> style.withColor(color).withItalic(false))
						.append(Component.literal(" - " + reasons.get(i))
								.withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))));
	}
}
