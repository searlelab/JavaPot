package org.searlelab.javapot.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class JavaPotCliTest {
	@Test
	void formatDurationCoversAllUnits() throws Exception {
		Method formatDuration = JavaPotCli.class.getDeclaredMethod("formatDuration", long.class);
		formatDuration.setAccessible(true);

		String seconds = (String) formatDuration.invoke(null, 3_100_000L);
		String minutes = (String) formatDuration.invoke(null, 120_000_000_000L);
		String hours = (String) formatDuration.invoke(null, 7_200_000_000_000L);
		String days = (String) formatDuration.invoke(null, 172_800_000_000_000L);

		assertTrue(seconds.contains("second"));
		assertTrue(minutes.contains("minute"));
		assertTrue(hours.contains("hour"));
		assertTrue(days.contains("day"));
	}

	@Test
	void formatValueHandlesNonFiniteAndPositiveNumbers() throws Exception {
		Method formatValue = JavaPotCli.class.getDeclaredMethod("formatValue", double.class);
		formatValue.setAccessible(true);

		String nonFinite = (String) formatValue.invoke(null, Double.NaN);
		String nonPositive = (String) formatValue.invoke(null, 0.0);
		String positive = (String) formatValue.invoke(null, 12.3456);

		assertEquals("0.00", nonFinite);
		assertEquals("0.00", nonPositive);
		assertTrue(positive.length() >= 2);
	}

	@Test
	void pluralizeHandlesSingularAndPlural() throws Exception {
		Method pluralize = JavaPotCli.class.getDeclaredMethod("pluralize", String.class, double.class);
		pluralize.setAccessible(true);

		String singular = (String) pluralize.invoke(null, "hour", 1.0);
		String plural = (String) pluralize.invoke(null, "hour", 2.0);

		assertEquals("hour", singular);
		assertEquals("hours", plural);
	}
}
