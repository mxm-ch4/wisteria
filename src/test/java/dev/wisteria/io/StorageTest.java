package dev.wisteria.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
	@TempDir
	Path game;

	private Storage storage(String module) {
		return new Storage(this.game.resolve("wisteria"), module);
	}

	@Test
	void keepsEachModuleInItsOwnFolder() throws Exception {
		Storage storage = this.storage("search");
		storage.writeText("cache/region.txt", "hello");
		assertEquals(Optional.of("hello"), storage.readText("cache/region.txt"));
		assertTrue(Files.isRegularFile(this.game.resolve("wisteria/data/search/cache/region.txt")));
		assertEquals(Optional.empty(), this.storage("xray").readText("cache/region.txt"), "another module does not see it");

		storage.writeBytes("blob.bin", new byte[] {1, 2, 3});
		assertArrayEquals(new byte[] {1, 2, 3}, storage.readBytes("blob.bin").orElseThrow());
		JsonObject json = new JsonObject();
		json.addProperty("count", 3);
		storage.writeJson("state.json", json);
		assertEquals(3, storage.readJson("state.json").orElseThrow().getAsJsonObject().get("count").getAsInt());
		storage.writeText("broken.json", "{ not json");
		assertEquals(Optional.empty(), storage.readJson("broken.json"), "bad JSON reads as missing, not as a crash");

		assertEquals(List.of("blob.bin", "broken.json", "cache/region.txt", "state.json"), storage.list());
		assertTrue(storage.delete("blob.bin"));
		assertFalse(storage.exists("blob.bin"));
	}

	@Test
	void writesReplaceTheWholeFile() {
		Storage storage = this.storage("notes");
		storage.writeText("a.txt", "a long first version");
		storage.writeText("a.txt", "short");
		assertEquals(Optional.of("short"), storage.readText("a.txt"));
		assertEquals(List.of("a.txt"), storage.list(), "no temporary file is left behind");
	}

	@Test
	void refusesNamesThatLeaveTheFolder() {
		Storage storage = this.storage("search");
		for (String bad : List.of("../x", "a/../../x", "/etc/passwd", "C:/x", "a\\b", "", ".", "..", "a//b", "a/b/c/d/e", ".hidden/x/../y")) {
			assertFalse(Storage.isValidName(bad), bad);
			assertThrows(IllegalArgumentException.class, () -> storage.writeText(bad, "x"), bad);
		}

		assertTrue(Storage.isValidName("db/waypoints.sqlite"));
		assertFalse(Storage.isValidName(".env"), "names do not start with a dot");
	}

	@Test
	void givesAPathForLibrariesThatOpenFilesThemselves() {
		Path database = this.storage("waypoints").file("db/points.sqlite");
		assertEquals(this.game.resolve("wisteria/data/waypoints/db/points.sqlite").toAbsolutePath(), database);
		assertTrue(Files.isDirectory(database.getParent()));
	}

	@Test
	void exportsGoToTheExportsFolderAndOutsideNeedsPermission() throws Exception {
		Path exported = this.storage("search").export("found.csv", "x,y,z".getBytes());
		assertEquals(this.game.resolve("wisteria/exports/found.csv").toAbsolutePath(), exported);

		Path outside = this.game.resolve("elsewhere/points.json");
		assertThrows(SecurityException.class, () -> this.storage("search").writeOutside(outside, new byte[] {1}));
		this.storage("waypoints").writeOutside(outside, new byte[] {1});
		assertTrue(Files.isRegularFile(outside));
	}
}
