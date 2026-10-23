package com.example.banhammer;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * Optionele lijst met owners in het bestand banhammer-owners.txt (in de hoofdmap van je server,
 * naast ops.json). Eén spelersnaam per regel; regels met # worden genegeerd.
 *
 * Staat er minstens één naam in, dan geldt:
 *  - alleen die owners mogen de Ophammer gebruiken
 *  - owners kunnen nooit worden gedeopt
 * Bestaat het bestand niet (of is het leeg), dan mag elke operator de Ophammer gebruiken.
 */
public final class Owners {
	private static final Path FILE = Path.of("banhammer-owners.txt");

	private Owners() {
	}

	public static List<String> names() {
		try {
			return Files.readAllLines(FILE).stream()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.toList();
		} catch (NoSuchFileException e) {
			return List.of();
		} catch (Exception e) {
			BanhammerMod.LOGGER.warn("Kon banhammer-owners.txt niet lezen", e);
			return List.of();
		}
	}

	public static boolean restricted() {
		return !names().isEmpty();
	}

	public static boolean isOwner(String name) {
		return names().stream().anyMatch(owner -> owner.equalsIgnoreCase(name));
	}
}
