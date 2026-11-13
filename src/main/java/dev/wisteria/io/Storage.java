package dev.wisteria.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A module's own folder, {@code .minecraft/wisteria/data/<module id>/}, and the only way a module touches files.
 * Names are relative paths of plain segments ({@code cache/r.0.0.json}); anything that could leave the folder
 * ({@code ..}, absolute paths, drive letters, backslashes) is refused. Writes go to a temporary file first and
 * replace the old one in a single move, so a crash never leaves half a file. Any thread; a module should do big
 * reads and writes off the client thread.
 */
public final class Storage {
	private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_\\-][A-Za-z0-9_.\\-]{0,63}");
	private static final int MAX_DEPTH = 4;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final String moduleId;
	private final Path directory;
	private final Path exports;
	private final Path wisteriaRoot;

	/** @param wisteriaRoot {@code .minecraft/wisteria} */
	public Storage(Path wisteriaRoot, String moduleId) {
		if (!SEGMENT.matcher(moduleId).matches()) {
			throw new IllegalArgumentException("Bad module id " + moduleId);
		}

		this.moduleId = moduleId;
		this.wisteriaRoot = wisteriaRoot.toAbsolutePath().normalize();
		this.directory = this.wisteriaRoot.resolve("data").resolve(moduleId);
		this.exports = this.wisteriaRoot.resolve("exports");
	}

	/** The module's folder. Created on the first write. */
	public Path directory() {
		return this.directory;
	}

	/** True if {@code name} is a file name this storage accepts. */
	public static boolean isValidName(String name) {
		String[] segments = name.split("/", -1);
		if (segments.length == 0 || segments.length > MAX_DEPTH) {
			return false;
		}

		for (String segment : segments) {
			if (!SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The path of a file in the module's folder, for libraries that open files themselves (an SQLite
	 * database, say). Its parent folder exists when this returns.
	 */
	public Path file(String name) {
		Path file = this.resolve(this.directory, name);
		try {
			Files.createDirectories(file.getParent());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return file;
	}

	public boolean exists(String name) {
		return Files.isRegularFile(this.resolve(this.directory, name));
	}

	public Optional<String> readText(String name) {
		return this.readBytes(name).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
	}

	public void writeText(String name, String text) {
		this.writeBytes(name, text.getBytes(StandardCharsets.UTF_8));
	}

	/** @return the bytes, or empty if there is no such file */
	public Optional<byte[]> readBytes(String name) {
		Path file = this.resolve(this.directory, name);
		try {
			return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public void writeBytes(String name, byte[] data) {
		atomicWrite(this.resolve(this.directory, name), data);
	}

	/** @return the parsed JSON, or empty if the file is missing or is not valid JSON */
	public Optional<JsonElement> readJson(String name) {
		return this.readText(name).flatMap(text -> {
			try {
				return Optional.of(JsonParser.parseString(text));
			} catch (JsonParseException e) {
				return Optional.empty();
			}
		});
	}

	public void writeJson(String name, JsonElement json) {
		this.writeText(name, GSON.toJson(json));
	}

	public boolean delete(String name) {
		try {
			return Files.deleteIfExists(this.resolve(this.directory, name));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Every file in the module's folder, as names relative to it, sorted. */
	public List<String> list() {
		if (!Files.isDirectory(this.directory)) {
			return List.of();
		}

		List<String> names = new ArrayList<>();
		try (Stream<Path> files = Files.walk(this.directory, MAX_DEPTH)) {
			files.filter(Files::isRegularFile)
				.map(file -> this.directory.relativize(file).toString().replace('\\', '/'))
				.filter(name -> !name.endsWith(".tmp"))
				.sorted()
				.forEach(names::add);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return names;
	}

	/**
	 * Writes a file the user asked for into {@code .minecraft/wisteria/exports/}, where they can find it.
	 *
	 * @return where it went
	 */
	public Path export(String name, byte[] data) {
		Path file = this.resolve(this.exports, name);
		atomicWrite(file, data);
		return file;
	}

	/**
	 * Writes a file anywhere, for an explicit action of the user (export to a path they chose). Only for
	 * modules with {@link Permission#WRITE_OUTSIDE}.
	 */
	public void writeOutside(Path target, byte[] data) {
		Permission.WRITE_OUTSIDE.check(this.moduleId);
		atomicWrite(target.toAbsolutePath().normalize(), data);
	}

	private Path resolve(Path base, String name) {
		if (!isValidName(name)) {
			throw new IllegalArgumentException("\"" + name + "\" is not a valid file name: use letters, digits, . _ - and / between folders");
		}

		Path file = base.resolve(name).normalize();
		if (!file.startsWith(base) || !file.startsWith(this.wisteriaRoot)) {
			throw new IllegalArgumentException("\"" + name + "\" points outside " + base);
		}

		return file;
	}

	private static void atomicWrite(Path file, byte[] data) {
		try {
			Files.createDirectories(file.getParent());
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(temporary, data);
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
