package org.searlelab.javapot.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class BackportValueTypesTest {
	@Test
	void javaPotPeptideImplementsValueSemantics() {
		JavaPotPeptide peptide = new JavaPotPeptide(1.5, 0.01, 0.02, false, "psm_1", "PEPTIDE");
		JavaPotPeptide same = new JavaPotPeptide(1.5, 0.01, 0.02, false, "psm_1", "PEPTIDE");
		JavaPotPeptide different = new JavaPotPeptide(1.5, 0.01, 0.02, true, "psm_1", "PEPTIDE");

		assertEquals(1.5, peptide.score());
		assertEquals(0.01, peptide.qValue());
		assertEquals(0.02, peptide.pep());
		assertEquals("psm_1", peptide.psmId());
		assertEquals("PEPTIDE", peptide.peptideSequence());
		assertEquals(peptide, same);
		assertEquals(peptide.hashCode(), same.hashCode());
		assertNotEquals(peptide, different);
		assertTrue(peptide.toString().contains("isDecoy=false"));
	}

	@Test
	void javaPotRunResultImplementsValueSemantics() {
		ArrayList<JavaPotPeptide> peptides = new ArrayList<JavaPotPeptide>();
		peptides.add(new JavaPotPeptide(2.0, 0.005, 0.01, false, "psm_2", "PEP2"));
		ArrayList<JavaPotPeptide> psms = new ArrayList<JavaPotPeptide>(peptides);
		ArrayList<Path> files = new ArrayList<Path>();
		files.add(Paths.get("out.peptides.tsv"));

		JavaPotRunResult result = new JavaPotRunResult(peptides, psms, 0.4, 0.5, files);
		JavaPotRunResult same = new JavaPotRunResult(peptides, psms, 0.4, 0.5, files);
		JavaPotRunResult different = new JavaPotRunResult(peptides, psms, 0.4, null, files);

		assertEquals(peptides, result.peptides());
		assertEquals(psms, result.psms());
		assertEquals(0.4, result.psmPi0());
		assertEquals(0.5, result.peptidePi0());
		assertEquals(files, result.writtenFiles());
		assertEquals(result, same);
		assertEquals(result.hashCode(), same.hashCode());
		assertNotEquals(result, different);
		assertTrue(result.toString().contains("writtenFiles"));
	}
}
