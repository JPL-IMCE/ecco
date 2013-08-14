/*******************************************************************************
 * This file is part of ecco.
 * 
 * ecco is distributed under the terms of the GNU Lesser General Public License (LGPL), Version 3.0.
 *  
 * Copyright 2011-2013, The University of Manchester
 *  
 * ecco is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *  
 * ecco is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even 
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser 
 * General Public License for more details.
 *  
 * You should have received a copy of the GNU Lesser General Public License along with ecco.
 * If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/
package uk.ac.manchester.cs.diff.concept;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.manchester.cs.diff.concept.changeset.ConceptChangeSet;
import uk.ac.manchester.cs.diff.utils.SilentChangeBroadcastStrategy;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import com.clarkparsia.owlapi.modularity.locality.LocalityClass;
import com.clarkparsia.owlapi.modularity.locality.SyntacticLocalityEvaluator;

/**
 * @author Rafael S. Goncalves <br/>
 * Information Management Group (IMG) <br/>
 * School of Computer Science <br/>
 * University of Manchester <br/>
 */
public class GrammarDiffv1 extends SubconceptDiff {
	private OWLOntologyManager man;
	private boolean includeAllRoles = false;

	/**
	 * Constructor for grammar diff w.r.t. sigma := sig(O1) U sig(O2)
	 * @param ont1	Ontology 1
	 * @param ont2	Ontology 2
	 * @param outputDir	Output directory
	 * @param verbose	Verbose mode
	 */
	public GrammarDiffv1(OWLOntology ont1, OWLOntology ont2, String outputDir, boolean verbose) {
		super(ont1, ont2, outputDir, verbose);
		man = OWLManager.createOWLOntologyManager();
		man.setDefaultChangeBroadcastStrategy(new SilentChangeBroadcastStrategy());
	}
	
	
	/**
	 * Constructor for grammar diff w.r.t. given signature
	 * @param ont1	Ontology 1
	 * @param ont2	Ontology 2
	 * @param sig	Signature (set of concept names)
	 * @param outputDir	Output directory
	 * @param verbose	Verbose mode
	 */
	public GrammarDiffv1(OWLOntology ont1, OWLOntology ont2, Set<OWLEntity> sig, String outputDir, boolean verbose) {
		super(ont1, ont2, sig, outputDir, verbose);
		man = OWLManager.createOWLOntologyManager();
		man.setDefaultChangeBroadcastStrategy(new SilentChangeBroadcastStrategy());
	}


	/**
	 * Get the concept-based change set between the given ontologies 
	 * @return Concept-based change set
	 * @throws InterruptedException
	 * @throws OWLOntologyCreationException 
	 */
	public ConceptChangeSet getDiff() throws InterruptedException, OWLOntologyCreationException {
		long start = System.currentTimeMillis();
		if(verbose) System.out.println("Input signature: sigma contains " + sig.size() + " concept names");
		
		if(sig.size() < ont1.getClassesInSignature().size() && sig.size() < ont2.getClassesInSignature().size()) {
			if(includeAllRoles) {
				Set<OWLObjectProperty> allRoles = new Signature().getUnionRoles(ont1, ont2);
				sig.addAll(allRoles);
				System.out.println("\tInflated sigma with " + allRoles.size() + " roles");
			}
			
			ont1 = man.createOntology(new SyntacticLocalityModuleExtractor(ont1.getOWLOntologyManager(), ont1, ModuleType.STAR).extract(sig));
			ont2 = man.createOntology(new SyntacticLocalityModuleExtractor(ont2.getOWLOntologyManager(), ont2, ModuleType.STAR).extract(sig));
			
			if(verbose) {
				System.out.println("mod(sigma)_1 size: " + ont1.getLogicalAxiomCount() + " axioms, " +
						"nr. classes: " + ont1.getClassesInSignature().size() + ", nr. roles: " + ont1.getObjectPropertiesInSignature().size());
				System.out.println("mod(sigma)_2 size: " + ont2.getLogicalAxiomCount() + " axioms, "+ 
						"nr. classes: " + ont2.getClassesInSignature().size() + ", nr. roles: " + ont2.getObjectPropertiesInSignature().size() + "\n");
			}
		}
		
		Map<OWLClass,OWLClassExpression> map = getSubConceptsMapping("E");
		classifyOntologies(ont1, ont2);
		Set<OWLClass> affected = computeChangeWitnesses(map);
		
		long mid = System.currentTimeMillis();
		
		// Remove extra axioms and create fresh reasoner instances
		ont1.getOWLOntologyManager().removeAxioms(ont1, extraAxioms);
		ont2.getOWLOntologyManager().removeAxioms(ont2, extraAxioms);

		classifyOntologies(ont1, ont2);
		
		changeSet = splitDirectIndirectChanges(affected, ont1reasoner, ont2reasoner);
		long end = System.currentTimeMillis();
		
		changeSet.setEntailmentDiffTime((mid-start)/1000.0);
		changeSet.setPartitioningTime((end-mid)/1000.0);
		
		if(verbose) printDiff(changeSet);
		System.out.println("finished (total diff time: " + (end-start)/1000.0 + " secs)");	
		return changeSet;
	}


	/**
	 * Create a mapping between a new term "TempX" and each sub-concept, and add the appropriate
	 * equivalence axioms to each ontology
	 * @param diff	Type of diff that determines the kind of axiom to be added 
	 * @return Map of new terms to subconcepts
	 */
	private Map<OWLClass,OWLClassExpression> getSubConceptsMapping(String diff) {
		Set<OWLClassExpression> scs = collectSCs();
		Map<OWLClass,OWLClassExpression> map = new HashMap<OWLClass,OWLClassExpression>();
		
		Set<OWLObjectProperty> roles = new Signature().getSharedRoles(ont1, ont2);		
		Set<OWLClass> sig = new Signature().getSharedConceptNames(ont1, ont2);

		if(verbose) System.out.println("Inflating ontologies...");
		if(verbose) System.out.println("\tShared roles: " + roles.size() + ", shared classes: " + sig.size());
		
		// Collect witnesses
		Set<OWLClassExpression> wits = new HashSet<OWLClassExpression>(scs);
		scs.addAll(sig);
		wits.addAll(getExistentialWitnesses(scs, roles));
		wits.addAll(getUniversalWitnesses(scs, roles));
		wits.addAll(getNegationWitnesses(scs));		
		if(verbose) System.out.println("\tTotal nr. of witnesses: " + wits.size());
		
		SyntacticLocalityEvaluator eval = new SyntacticLocalityEvaluator(LocalityClass.TOP_BOTTOM);
		int counter = 1;
		extraAxioms = new HashSet<OWLAxiom>();
		for(OWLClassExpression ce : wits) {
			OWLClass c = df.getOWLClass(IRI.create("diffSubc_" + counter));
			map.put(c, ce);
			OWLAxiom ax = null;
			if (diff.equals("R"))
				ax = df.getOWLSubClassOfAxiom(c, ce);
			else if (diff.equals("L") || diff.equals("E"))
				ax = df.getOWLEquivalentClassesAxiom(c, ce);
			
			if(ax!=null && !eval.isLocal(ax, this.sig)) extraAxioms.add(ax); 
			counter++;
		}
		ont1.getOWLOntologyManager().addAxioms(ont1, extraAxioms);
		ont2.getOWLOntologyManager().addAxioms(ont2, extraAxioms);
		if(verbose) System.out.println("done (nr. of extra axioms: " + extraAxioms.size() + ")");
		return map;
	}
	
	
	/**
	 * Get the set of possible negation witnesses
	 * @param sc	Set of subconcepts
	 * @return Set of negation witnesses
	 */
	private Set<OWLClassExpression> getNegationWitnesses(Set<? extends OWLClassExpression> sc) {
		Set<OWLClassExpression> out = new HashSet<OWLClassExpression>();
		for(OWLClassExpression c : sc)
			out.add(df.getOWLObjectComplementOf(c));
		if(verbose) System.out.println("\tNegation witnesses: " + out.size());
		return out;
	}
	
	
	/**
	 * Get the set of possible existential witnesses
	 * @param sc	Set of subconcepts
	 * @param roles	Set of role names
	 * @return Set of existential witnesses
	 */
	private Set<OWLClassExpression> getExistentialWitnesses(Set<? extends OWLClassExpression> sc, Set<OWLObjectProperty> roles) {
		Set<OWLClassExpression> out = new HashSet<OWLClassExpression>();
		for(OWLClassExpression c : sc) {
			for(OWLObjectProperty r : roles)
				out.add(df.getOWLObjectSomeValuesFrom(r, c));
		}
		if(verbose) System.out.println("\tExistential witnesses: " + out.size());
		return out;
	}
	
	
	/**
	 * Get the set of possible universal witnesses
	 * @param sc	Set of subconcepts
	 * @param roles	Set of role names
	 * @return Set of universal witnesses
	 */
	private Set<OWLClassExpression> getUniversalWitnesses(Set<? extends OWLClassExpression> sc, Set<OWLObjectProperty> roles) {
		Set<OWLClassExpression> out = new HashSet<OWLClassExpression>();
		for(OWLClassExpression c : sc) {
			for(OWLObjectProperty r : roles)
				out.add(df.getOWLObjectAllValuesFrom(r, c));
		}
		if(verbose) System.out.println("\tUniversal witnesses: " + out.size());
		return out;
	}
	
	
	/**
	 * Get the set of conjunction witnesses
	 * @param sc	Set of subconcepts within the modules
	 * @return Set of conjunction witnesses
	 */
	@SuppressWarnings("unused")
	private Set<OWLClassExpression> getConjunctionWitnesses(Set<OWLClassExpression> sc) {
		Set<OWLClassExpression> out = new HashSet<OWLClassExpression>();
		OWLClassExpression[] scarr = sc.toArray(new OWLClassExpression[sc.size()]);
		for(int i = 0; i < sc.size(); i++) {
			for(int j = i+1; j < sc.size(); j++)
				out.add(df.getOWLObjectIntersectionOf(scarr[i], scarr[j]));
		}
		if(verbose) System.out.println("\tConjunction witnesses: " + out.size());
		return out;
	}
	
	
	/**
	 * Get the set of disjunction witnesses
	 * @param sc	Set of subconcepts within the modules
	 * @return Set of disjunction witnesses
	 */
	@SuppressWarnings("unused")
	private Set<OWLClassExpression> getDisjunctionWitnesses(Set<OWLClassExpression> sc) {
		Set<OWLClassExpression> out = new HashSet<OWLClassExpression>();
		OWLClassExpression[] scarr = sc.toArray(new OWLClassExpression[sc.size()]);
		for(int i = 0; i < sc.size(); i++) {
			for(int j = i+1; j < sc.size(); j++)
				out.add(df.getOWLObjectUnionOf(scarr[i], scarr[j]));
		}
		if(verbose) System.out.println("\tDisjunction witnesses: " + out.size());
		return out;
	}
}