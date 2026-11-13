package dev.wisteria.io;

import java.util.Map;
import java.util.Set;

/**
 * What a module may do beyond its own data folder. The list is part of the core and is changed only by a core
 * task, together with docs/module-api.md: a module never grants itself anything.
 */
public enum Permission {
	/** Outgoing HTTPS requests, to hosts the user allowed with {@code .net allow}. */
	NET,
	/** Reading tokens and keys from {@code .minecraft/wisteria/.env}. */
	SECRETS,
	/** Writing a file outside {@code .minecraft/wisteria/}, on an explicit action of the user. */
	WRITE_OUTSIDE;

	private static final Map<Permission, Set<String>> GRANTED = Map.of(
		NET, Set.of("notifier"),
		SECRETS, Set.of("notifier"),
		WRITE_OUTSIDE, Set.of("waypoints"));

	/** @param moduleId the module's {@code id()} */
	public boolean grantedTo(String moduleId) {
		return GRANTED.get(this).contains(moduleId);
	}

	/** @throws SecurityException when the module does not have this permission */
	public void check(String moduleId) {
		if (!this.grantedTo(moduleId)) {
			throw new SecurityException(moduleId + " does not have the " + this + " permission (see docs/module-api.md)");
		}
	}
}
