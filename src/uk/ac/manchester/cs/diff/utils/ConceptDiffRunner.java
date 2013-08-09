package uk.ac.manchester.cs.diff.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.manchester.cs.diff.concept.GrammarDiff;
import uk.ac.manchester.cs.diff.concept.Signature;

public class ConceptDiffRunner {

	
	public static void serializeSample(Set<OWLClass> sampleSet, String outputDir) {
		String sigList = "";
		for(OWLClass c : sampleSet) sigList += c.getIRI() + "\n";
		try {
			new File(outputDir).mkdirs();
			File file = new File(outputDir + "randomSample.txt");
			Writer output = new BufferedWriter(new FileWriter(file, false));
			System.out.println("Saved random sample at: " + file.getAbsolutePath());
			output.write(sigList);
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Tester class
	 * @param 0: ontology1
	 * @param 1: ontology2
	 * @param 2: output directory
	 * @param 3: signature file
	 * @throws OWLOntologyCreationException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, InterruptedException {
		File f1 = new File(args[0]), f2 = new File(args[1]);
		OWLOntologyManager man1 = OWLManager.createOWLOntologyManager(), man2 = OWLManager.createOWLOntologyManager();
		
		OWLOntology ont1 = man1.loadOntologyFromOntologyDocument(f1);
		System.out.println("Loaded ontology 1: " + f1.getAbsolutePath());
		
		OWLOntology ont2 = man2.loadOntologyFromOntologyDocument(f2);
		System.out.println("Loaded ontology 2: " + f2.getAbsolutePath());
		
		String outputDir = args[2];
		
		// Remove abox for NCIt
		man1.removeAxioms(ont1, ont1.getABoxAxioms(true)); man2.removeAxioms(ont2, ont2.getABoxAxioms(true));
		
		Set<OWLClass> sampleSet = null;
		if(args[3] != null)
			sampleSet = new Signature().getSignatureFromFile(new File(args[3]));
		else {
			// Get random signature sample
			SignatureSampler sampler = new SignatureSampler(ont1, ont2);
			sampleSet = sampler.getSample(381);
			serializeSample(sampleSet, outputDir);
		}
		
		// Instantiate diff
		GrammarDiff diff = new GrammarDiff(ont1, ont2, sampleSet, outputDir, true);
		diff.getDiff();
	}
}