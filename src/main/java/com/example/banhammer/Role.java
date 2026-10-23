package com.example.banhammer;

/** Rollen van laag naar hoog. Alles wordt server-side gecontroleerd. */
public enum Role {
	NONE(0, "Geen"),
	STAFF(1, "Staff"),
	MODERATOR(2, "Moderator"),
	ADMIN(3, "Admin"),
	OWNER(4, "Owner");

	public final int level;
	public final String label;

	Role(int level, String label) {
		this.level = level;
		this.label = label;
	}

	public boolean atLeast(Role other) {
		return this.level >= other.level;
	}

	public static Role parse(String text) {
		for (Role role : values()) {
			if (role.name().equalsIgnoreCase(text)) {
				return role;
			}
		}
		return NONE;
	}
}
