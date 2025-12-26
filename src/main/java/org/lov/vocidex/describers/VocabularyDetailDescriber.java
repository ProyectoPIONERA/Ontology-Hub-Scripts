package org.lov.vocidex.describers;

import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.lov.SPARQLRunner;
import org.lov.vocidex.VocidexDocument;
import org.lov.vocidex.extract.VocabularyTermExtractor;

import com.hp.hpl.jena.rdf.model.Resource;

import java.util.Objects;

/**
 * Describes a vocabulary provided in an RDF graph by describing
 * the details of all classes, properties and datatypes.
 * 
 * @author Richard Cyganiak
 */
public class VocabularyDetailDescriber extends SPARQLDescriber {
	private final String prefix;
	
	/**
	 * @param source Model containing declarations of vocabulary terms
	 */
	public VocabularyDetailDescriber(SPARQLRunner source) {
		this(source, null);
	}

	/**
	 * @param source Model containing declarations of vocabulary terms
	 * @param prefix Prefix to be used for creating prefixed names; may be null 
	 */
	public VocabularyDetailDescriber(SPARQLRunner source, String prefix) {
		super(source);
		this.prefix = prefix;
	}

	/**
	 * TODO: This actually ignores the first argument. Smells of a problem!
	 */
	@Override
	public void describe(Resource vocabulary, ObjectNode descriptionRoot) {
		ArrayNode classes = mapper.createArrayNode();
		ArrayNode properties = mapper.createArrayNode();
		ArrayNode datatypes = mapper.createArrayNode();
        ArrayNode individuals = mapper.createArrayNode();
		for (VocidexDocument document: new VocabularyTermExtractor(getSource(), prefix, null)) {
            System.out.println("TIPO DE DOCUMENTO => " + document.getType());
			if (Objects.equals(document.getType(), ClassDescriber.TYPE)) {
				classes.add(document.getRoot());
			} else if (Objects.equals(document.getType(), PropertyDescriber.TYPE)) {
				properties.add(document.getRoot());
			} else if (Objects.equals(document.getType(), DatatypeDescriber.TYPE)) {
                datatypes.add(document.getRoot());
            } else if (Objects.equals(document.getType(), IndividualDescriber.TYPE)) {
                individuals.add(document.getRoot());
                System.out.println("Size of the array" + individuals.size());
            }
		}
		descriptionRoot.put("classes", classes);
		descriptionRoot.put("properties", properties);
		descriptionRoot.put("datatypes", datatypes);
        descriptionRoot.put("individuals", individuals);
	}
}
