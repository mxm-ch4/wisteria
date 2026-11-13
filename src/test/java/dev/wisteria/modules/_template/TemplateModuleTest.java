package dev.wisteria.modules._template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.module.Module;
import dev.wisteria.modules.Modules;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The unit test every module should have: it runs without the game, in milliseconds. Copy and adapt it;
 * see docs/module-api.md.
 */
class TemplateModuleTest {
	@TempDir
	Path gameDirectory;

	/** The shipped modules plus the one under test, as if it were registered in Modules. */
	private Client start(FakePlatform platform) {
		List<Module> modules = new ArrayList<>(Modules.create());
		modules.add(new TemplateModule());
		return new Client(platform, modules);
	}

	@Test
	void switchesOnFromChatAndRunsOnlyWhileOn() {
		FakePlatform platform = new FakePlatform(this.gameDirectory);
		Client client = this.start(platform);
		TemplateModule module = (TemplateModule)client.modules().get("template");

		platform.tick(5);
		assertEquals(0, module.ticks(), "handlers do not run while off");
		platform.typeInChat(".template");
		assertTrue(module.isEnabled());
		platform.tick(5);
		assertEquals(5, module.ticks());
		platform.typeInChat(".template");
		assertFalse(module.isEnabled());
	}

	@Test
	void settingsAreSafeByDefaultAndSurviveARestart() {
		FakePlatform platform = new FakePlatform(this.gameDirectory);
		Client client = this.start(platform);
		TemplateModule module = (TemplateModule)client.modules().get("template");
		assertFalse(module.aggressive(), "aggressive options default to off");

		platform.typeInChat(".template range 6");
		platform.typeInChat(".template mode firm");
		platform.typeInChat(".template aggressive on");
		platform.typeInChat(".template range 100");
		assertEquals("6", module.setting("range").format(), "values are clamped to the declared range");
		platform.stop();

		Client after = this.start(new FakePlatform(this.gameDirectory));
		TemplateModule restored = (TemplateModule)after.modules().get("template");
		assertEquals("6", restored.setting("range").format());
		assertEquals(TemplateModule.Mode.FIRM, restored.mode());
		assertTrue(restored.aggressive());
	}
}
