package dev.wisteria.privacy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Clear My Data: deletes everything inside the client's data folder and nothing else. The folder itself stays;
 * links are deleted as links, never followed out of it.
 */
public final class DataWipe {
	private DataWipe() {
	}

	/**
	 * @return how many files (not folders) were deleted
	 * @throws IllegalArgumentException for a filesystem root
	 */
	public static int wipe(Path root) {
		Path folder = root.toAbsolutePath().normalize();
		if (folder.getParent() == null) {
			throw new IllegalArgumentException("Refusing to clear a filesystem root: " + folder);
		}

		if (!Files.isDirectory(folder)) {
			return 0;
		}

		List<Path> inside;
		try (Stream<Path> walk = Files.walk(folder)) {
			inside = walk.filter(path -> !path.equals(folder)).sorted(Comparator.reverseOrder()).toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		int files = 0;
		for (Path path : inside) {
			if (!path.toAbsolutePath().normalize().startsWith(folder)) {
				continue;
			}

			try {
				boolean file = !Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
				if (Files.deleteIfExists(path) && file) {
					files++;
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		return files;
	}
}
