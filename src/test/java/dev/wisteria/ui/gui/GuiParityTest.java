package dev.wisteria.ui.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wisteria.Client;
import dev.wisteria.FakePlatform;
import dev.wisteria.FakeRenderer;
import dev.wisteria.command.Command;
import dev.wisteria.platform.Key;
import dev.wisteria.ui.Animation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Everything a chat command does can be done in the ClickGUI: each command names its control, and it must be there. */
class GuiParityTest {
	@TempDir
	Path game;

	@Test
	void everyCommandHasItsControlInTheMenu() {
		AtomicLong clock = new AtomicLong(1_000_000_000L);
		Animation.useClock(clock::get);
		try {
			FakePlatform platform = new FakePlatform(this.game);
			Client client = new Client(platform);
			platform.press(Key.RIGHT_SHIFT, false);
			ClickGuiScreen screen = (ClickGuiScreen)platform.openScreen;
			screen.render(new FakeRenderer(960.0F, 540.0F), 0.0F, 0.0F, 1.0F);
			screen.panels().forEach(panel -> panel.rows().forEach(row -> row.setExpanded(true)));
			for (int i = 0; i < 3; i++) {
				clock.addAndGet(1_000_000_000L);
				screen.render(new FakeRenderer(960.0F, 540.0F), 0.0F, 0.0F, 1.0F);
			}

			List<String> ids = screen.controls().snapshot().stream().map(Controls.Control::id).toList();
			List<String> missing = new ArrayList<>();
			for (Command command : client.commands().all()) {
				String wanted = command.guiControl();
				if (wanted == null || ids.stream().noneMatch(id -> id.startsWith(wanted))) {
					missing.add(command.name() + " -> " + wanted);
				}
			}

			assertTrue(missing.isEmpty(), "commands with no control in the ClickGUI: " + missing);
		} finally {
			Animation.useRealClock();
		}
	}
}
