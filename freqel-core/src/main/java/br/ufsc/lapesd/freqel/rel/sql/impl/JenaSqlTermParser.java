package br.ufsc.lapesd.freqel.rel.sql.impl;

import br.ufsc.lapesd.freqel.jena.JenaWrappers;
import br.ufsc.lapesd.freqel.model.term.Term;
import br.ufsc.lapesd.freqel.rel.common.RelationalTermParser;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.impl.LiteralImpl;
import org.apache.jena.rdf.model.impl.ResourceImpl;

import javax.annotation.Nullable;

public class JenaSqlTermParser implements RelationalTermParser {
    @Override
    public @Nullable Term parseTerm(@Nullable Object sqlObject) {
        if (sqlObject == null || (sqlObject instanceof Term))
            return (Term)sqlObject;
        return JenaWrappers.fromJena(parseNode(sqlObject));
    }

    @Override
    public @Nullable RDFNode parseNode(@Nullable Object sqlObject) {
        if (sqlObject == null || sqlObject instanceof RDFNode) {
            return (RDFNode)sqlObject;
        } else if (sqlObject instanceof Node) {
            Node node = (Node) sqlObject;
            if (node.isURI() || node.isBlank())
                return new ResourceImpl(node, null);
            else if (node.isLiteral())
                return new LiteralImpl(node, null);
            else
                throw new IllegalArgumentException("Cannot convert "+node+" to an RDFNode");
        } else if (sqlObject instanceof Term) {
            return JenaWrappers.toJena((Term)sqlObject);
        }
        return ResourceFactory.createTypedLiteral(sqlObject);
    }
}
