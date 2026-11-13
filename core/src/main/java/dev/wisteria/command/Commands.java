package dev.wisteria.command;

import dev.wisteria.Client;
import dev.wisteria.command.Command.CommandException;
import dev.wisteria.command.Command.Output;
import dev.wisteria.config.ConfigManager;
import dev.wisteria.module.Category;
import dev.wisteria.module.Module;
import dev.wisteria.setting.Setting;
import dev.wisteria.social.Relations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** The built-in commands. */
final class Commands {
	private Commands() {
	}

	static void registerAll(CommandManager manager) {
		manager.register(new Help());
		manager.register(new Modules());
		manager.register(new Toggle());
		manager.register(new Bind());
		manager.register(new Set());
		manager.register(new Reset());
		manager.register(new Relation("friend", List.of("f"), Relations.Relation.FRIEND, "friends"));
		manager.register(new Relation("enemy", List.of(), Relations.Relation.ENEMY, "enemies"));
		manager.register(new Profile());
		manager.register(new Prefix());
	}

	private static Module module(Client client, String name) {
		Module module = client.modules().get(name);
		if (module == null) {
			throw new CommandException("No module called \"" + name + "\"");
		}

		return module;
	}

	private static Setting<?> setting(Module module, String name) {
		Setting<?> setting = module.setting(name);
		if (setting == null) {
			throw new CommandException(module.name() + " has no setting \"" + name + "\"; it has: " + settingIds(module));
		}

		return setting;
	}

	private static String settingIds(Module module) {
		return module.settings().isEmpty() ? "none" : module.settings().stream().map(Setting::id).collect(Collectors.joining(", "));
	}

	private static List<String> moduleIds(Client client) {
		return client.modules().all().stream().map(Module::id).toList();
	}

	private static List<String> settingIdList(Module module) {
		return module.settings().stream().map(Setting::id).toList();
	}

	private static String onOff(boolean enabled) {
		return enabled ? "on" : "off";
	}

	private static String join(List<String> args, int from) {
		return String.join(" ", args.subList(from, args.size()));
	}

	private static void describe(Module module, Output out) {
		out.print(module.name() + " is " + onOff(module.isEnabled()) + ", bind: " + module.bind().format());
		for (Setting<?> setting : module.settings()) {
			out.print("  " + setting.id() + " = " + setting.format() + "  (" + setting.description() + ")");
		}
	}

	private static void applySetting(Module module, List<String> args, Output out) {
		Setting<?> setting = setting(module, args.get(0));
		if (args.size() == 1) {
			out.print(module.name() + " " + setting.id() + " = " + setting.format() + "  (" + setting.description() + ")");
			return;
		}

		setting.parse(join(args, 1));
		out.print(module.name() + " " + setting.id() + " = " + setting.format());
	}

	/** ".esp" toggles, ".esp range" shows a setting, ".esp range 48" sets it. */
	static void moduleShorthand(Client client, Module module, List<String> args, Output out) {
		if (args.isEmpty()) {
			if (client.setEnabled(module, !module.isEnabled())) {
				out.print(module.name() + " " + onOff(module.isEnabled()));
			}
		} else {
			applySetting(module, args, out);
		}
	}

	static List<String> completeModuleShorthand(Module module, List<String> args) {
		if (args.size() == 1) {
			return settingIdList(module);
		}

		Setting<?> setting = module.setting(args.get(0));
		return args.size() == 2 && setting != null ? setting.suggestions() : List.of();
	}

	private static final class Help extends Command {
		Help() {
			super("help", List.of("?"), "[command]", "Lists commands or explains one");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			String prefix = client.commands().prefix();
			if (!args.isEmpty()) {
				Command command = client.commands().get(args.get(0));
				if (command == null) {
					throw new CommandException("No command called \"" + args.get(0) + "\"");
				}

				out.print(prefix + command.name() + " " + command.usage() + ": " + command.description());
				if (!command.aliases().isEmpty()) {
					out.print("  also: " + command.aliases().stream().map(alias -> prefix + alias).collect(Collectors.joining(", ")));
				}

				return;
			}

			for (Command command : client.commands().all()) {
				out.print(prefix + command.name() + " " + command.usage() + ": " + command.description());
			}

			out.print("A module name is a command too: " + prefix + "esp toggles it, " + prefix + "esp range 48 changes a setting");
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			return args.size() == 1 ? client.commands().all().stream().map(Command::name).toList() : List.of();
		}
	}

	private static final class Modules extends Command {
		Modules() {
			super("modules", List.of("mods"), "", "Lists modules by category");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			for (Category category : Category.values()) {
				List<Module> modules = client.modules().in(category);
				if (!modules.isEmpty()) {
					out.print(category.title() + ": " + modules.stream()
						.map(module -> module.name() + (module.isEnabled() ? " [on]" : ""))
						.collect(Collectors.joining(", ")));
				}
			}
		}
	}

	private static final class Toggle extends Command {
		Toggle() {
			super("toggle", List.of("t"), "<module> [on|off]", "Switches a module on or off");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			if (args.isEmpty() || args.size() > 2) {
				throw new CommandException("Usage: " + client.commands().prefix() + "toggle " + this.usage());
			}

			Module module = module(client, args.get(0));
			boolean enabled = !module.isEnabled();
			if (args.size() == 2) {
				switch (args.get(1).toLowerCase(Locale.ROOT)) {
					case "on" -> enabled = true;
					case "off" -> enabled = false;
					default -> throw new CommandException("Expected on or off, got \"" + args.get(1) + "\"");
				}
			}

			if (client.setEnabled(module, enabled)) {
				out.print(module.name() + " " + onOff(module.isEnabled()));
			}
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			return args.size() == 1 ? moduleIds(client) : args.size() == 2 ? List.of("on", "off") : List.of();
		}
	}

	private static final class Bind extends Command {
		Bind() {
			super("bind", List.of("b"), "<module> [key|none]", "Shows or changes the key that toggles a module");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			if (args.isEmpty() || args.size() > 2) {
				throw new CommandException("Usage: " + client.commands().prefix() + "bind " + this.usage());
			}

			Module module = module(client, args.get(0));
			if (args.size() == 2) {
				module.bind().parse(args.get(1));
			}

			out.print(module.name() + " bind: " + module.bind().format());
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			if (args.size() == 1) {
				return moduleIds(client);
			}

			Module module = client.modules().get(args.get(0));
			return args.size() == 2 && module != null ? module.bind().suggestions() : List.of();
		}
	}

	private static final class Set extends Command {
		Set() {
			super("set", List.of("s"), "<module> [setting] [value]", "Shows or changes a module's settings");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			if (args.isEmpty()) {
				throw new CommandException("Usage: " + client.commands().prefix() + "set " + this.usage());
			}

			Module module = module(client, args.get(0));
			if (args.size() == 1) {
				describe(module, out);
			} else {
				applySetting(module, args.subList(1, args.size()), out);
			}
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			if (args.size() == 1) {
				return moduleIds(client);
			}

			Module module = client.modules().get(args.get(0));
			return module == null ? List.of() : completeModuleShorthand(module, args.subList(1, args.size()));
		}
	}

	private static final class Reset extends Command {
		Reset() {
			super("reset", List.of(), "<module> [setting]", "Puts a setting, or a whole module, back to defaults");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			if (args.isEmpty() || args.size() > 2) {
				throw new CommandException("Usage: " + client.commands().prefix() + "reset " + this.usage());
			}

			Module module = module(client, args.get(0));
			if (args.size() == 2) {
				Setting<?> setting = setting(module, args.get(1));
				setting.reset();
				out.print(module.name() + " " + setting.id() + " = " + setting.format());
			} else {
				boolean enabled = module.isEnabled();
				module.reset();
				client.setEnabled(module, enabled);
				out.print(module.name() + " settings and bind are back to defaults");
			}
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			if (args.size() == 1) {
				return moduleIds(client);
			}

			Module module = client.modules().get(args.get(0));
			return args.size() == 2 && module != null ? settingIdList(module) : List.of();
		}
	}

	private static final class Relation extends Command {
		private final Relations.Relation relation;
		private final String plural;

		Relation(String name, List<String> aliases, Relations.Relation relation, String plural) {
			super(name, aliases, "add|remove <name> / list", "Manages your " + plural + " list");
			this.relation = relation;
			this.plural = plural;
		}

		private List<String> names(Client client) {
			return this.relation == Relations.Relation.FRIEND ? client.relations().friends() : client.relations().enemies();
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			String action = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
			if (action.equals("list") && args.size() == 1) {
				List<String> names = this.names(client);
				out.print(names.isEmpty() ? "No " + this.plural + " yet" : names.size() + " " + this.plural + ": " + String.join(", ", names));
				return;
			}

			if (args.size() != 2 || !(action.equals("add") || action.equals("remove"))) {
				throw new CommandException("Usage: " + client.commands().prefix() + this.name() + " " + this.usage());
			}

			String name = args.get(1);
			if (!Relations.isValidName(name)) {
				throw new CommandException("\"" + name + "\" does not look like a player name");
			}

			if (action.equals("add")) {
				boolean changed = client.relations().set(name, this.relation);
				out.print(changed ? name + " added to " + this.plural : name + " is already in " + this.plural);
			} else if (client.relations().of(name) == this.relation) {
				client.relations().set(name, Relations.Relation.NEUTRAL);
				out.print(name + " removed from " + this.plural);
			} else {
				out.print(name + " is not in " + this.plural);
			}
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			if (args.size() == 1) {
				return List.of("add", "remove", "list");
			}

			if (args.size() == 2 && args.get(0).equalsIgnoreCase("remove")) {
				return this.names(client);
			}

			if (args.size() == 2 && args.get(0).equalsIgnoreCase("add") && client.platform().world().isLoaded()) {
				// Offer the players currently in view.
				List<String> names = new ArrayList<>();
				client.platform().world().entities(1.0F).forEach(entity -> {
					if (entity.kind() == dev.wisteria.compat.WorldBackend.EntityKind.PLAYER && !entity.self()) {
						names.add(entity.name());
					}
				});
				return names;
			}

			return List.of();
		}
	}

	private static final class Profile extends Command {
		Profile() {
			super("profile", List.of("p"), "list / load <name> / save <name> / delete <name>", "Switches between saved sets of module settings");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			ConfigManager config = client.config();
			String action = args.isEmpty() ? "" : args.get(0).toLowerCase(Locale.ROOT);
			if (action.equals("list") && args.size() == 1) {
				out.print("Profiles: " + config.profiles().stream()
					.map(name -> name.equals(config.profile()) ? name + " (active)" : name)
					.collect(Collectors.joining(", ")));
				return;
			}

			if (args.size() != 2) {
				throw new CommandException("Usage: " + client.commands().prefix() + "profile " + this.usage());
			}

			String name = args.get(1);
			switch (action) {
				case "load" -> {
					config.switchTo(name);
					out.print("Profile \"" + name + "\" loaded");
				}
				case "save" -> {
					config.saveAs(name);
					out.print("Saved as profile \"" + name + "\", now active");
				}
				case "delete" -> {
					config.delete(name);
					out.print("Profile \"" + name + "\" deleted");
				}
				default -> throw new CommandException("Usage: " + client.commands().prefix() + "profile " + this.usage());
			}
		}

		@Override
		public List<String> complete(Client client, List<String> args) {
			if (args.size() == 1) {
				return List.of("list", "load", "save", "delete");
			}

			BiFunction<String, String, Boolean> is = String::equalsIgnoreCase;
			if (args.size() == 2 && (is.apply(args.get(0), "load") || is.apply(args.get(0), "delete"))) {
				return client.config().profiles();
			}

			return List.of();
		}
	}

	private static final class Prefix extends Command {
		Prefix() {
			super("prefix", List.of(), "[symbols]", "Shows or changes what client commands start with");
		}

		@Override
		public void run(Client client, List<String> args, Output out) {
			if (args.size() > 1) {
				throw new CommandException("Usage: " + client.commands().prefix() + "prefix " + this.usage());
			}

			if (args.size() == 1) {
				client.commands().setPrefix(args.get(0));
			}

			out.print("Command prefix: " + client.commands().prefix());
		}
	}
}
