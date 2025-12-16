package org.lov.vocidex.describers;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Resource;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.lov.SPARQLRunner;
import org.lov.vocidex.JSONHelper;

public class IndividualDescriber extends TermDescriber {
    public final static String TYPE = "individual";

    public IndividualDescriber(SPARQLRunner source, String prefix, String tag) {
        super(source, prefix, tag);
    }

    @Override
    public void describe(Resource individual, ObjectNode descriptionRoot) {
        super.describe(TYPE, individual, descriptionRoot);

        // Get RDF classes (types) of this individual
        ResultSet rs = getSource().getResultSet("list-types-of-individual.sparql", "individual", individual);

        ArrayNode types = JSONHelper.createArray();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            if (qs.contains("type") && qs.get("type").isResource()) {
                ObjectNode node = JSONHelper.createObject();
                putString(node, "uri", qs.getResource("type").getURI());
                types.add(node);
            }
        }

        putURIArray(descriptionRoot, "types", types);
    }
}
