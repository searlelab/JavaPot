package org.searlelab.javapot.testutil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class TestCompat {
	private TestCompat() {
	}

	public static void writeString(Path path, String contents) throws IOException {
		Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
	}

	public static boolean filesEqual(Path left, Path right) throws IOException {
		return Arrays.equals(Files.readAllBytes(left), Files.readAllBytes(right));
	}
}
