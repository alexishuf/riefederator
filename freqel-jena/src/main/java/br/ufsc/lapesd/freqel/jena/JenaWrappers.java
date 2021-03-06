package br.ufsc.lapesd.freqel.jena;

import br.ufsc.lapesd.freqel.jena.model.prefix.PrefixMappingDict;
import br.ufsc.lapesd.freqel.jena.model.term.*;
import br.ufsc.lapesd.freqel.jena.model.term.node.*;
import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.model.prefix.PrefixDict;
import br.ufsc.lapesd.freqel.model.term.*;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;

import static org.apache.jena.datatypes.TypeMapper.getInstance;
import static org.apache.jena.rdf.model.ResourceFactory.*;

@SuppressWarnings("WeakerAccess")
public class JenaWrappers {
    private static final Logger logger = LoggerFactory.getLogger(JenaTerm.class);

    /* ~~~~~~~~~ fromJena(RDFNode) ~~~~~~~~~ */

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static JenaTerm fromJena(RDFNode node) {
        if (node == null)           return null;
        else if (node.isLiteral())  return fromJena(node.asLiteral());
        else if (node.isResource()) return fromJena(node.asResource());
        throw new UnsupportedOperationException("Do not know how to handle "+node);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static JenaURI fromURIResource(Resource resource) {
        return resource == null ? null : new JenaURI(resource);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static JenaBlank fromAnon(Resource resource) {
        return resource == null ? null : new JenaBlank(resource);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static JenaRes fromJena(Resource resource) {
        if (resource == null) return null;
        return  resource.isAnon() ? fromAnon(resource) : fromURIResource(resource);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static JenaLit fromJena(Literal literal) {
        return literal == null ? null : new JenaLit(literal);
    }

    public static JenaNodeTerm fromJena(@Nullable Node node) {
        if (node == null)
            return null;
        if (node.isBlank())
            return new JenaBlankNode((Node_Blank)node);
        else if (node.isURI())
            return new JenaURINode((Node_URI)node);
        else if (node.isLiteral())
            return new JenaLitNode((Node_Literal)node);
        else if (node.isVariable())
            return new JenaVarNode((Node_Variable)node);
        else
            throw new IllegalArgumentException("Cannot convert Node "+node+" to a Term");
    }

    /* ~~~~~~~~~ toJena(JenaTerm) ~~~~~~~~~ */

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static RDFNode toJena(JenaTerm   t) {return t == null ? null : t.getModelNode(); }
    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Resource toJena(JenaRes   t) {return t == null ? null : t.getModelNode().asResource();}
    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Resource toJena(JenaBlank t) {return t == null ? null : t.getModelNode().asResource();}
    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Resource toJena(JenaURI   t) {return t == null ? null : t.getModelNode().asResource();}
    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Literal  toJena(JenaLit   t) {return t == null ? null : t.getModelNode().asLiteral(); }

    /* ~~~~~~~~~ toJena(Term) ~~~~~~~~~ */

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Resource toJena(Res res) {
        if (res == null) return null;
        if (res instanceof JenaRes) return toJena((JenaRes)res);
        return res.isAnon() ? toJena(res.asBlank()) : toJena(res.asURI());
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Resource toJena(Blank blank) {
        if (blank == null) return null;
        if (blank instanceof JenaBlank) return toJena((JenaRes) blank);
        return createResource();
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Resource toJena(URI uri) {
        if (uri == null) return null;
        if (uri instanceof JenaURI) return toJena((JenaRes) uri);
        return createResource(uri.getURI());
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Literal toJena(Lit l) {
        if      (l == null)            return null;
        else if (l instanceof JenaLit) return toJena((JenaLit)l);
        else if (l.getLangTag() != null)
            return createLangLiteral(l.getLexicalForm(), l.getLangTag());

        String dtURI = l.getDatatype().getURI();
        RDFDatatype dt = getInstance().getTypeByName(dtURI);
        if (dt == null) {
            logger.warn("Unknown datatype {} for {}. Making it a plain literal", dtURI, l);
            return createPlainLiteral(l.getLexicalForm());
        }
        return createTypedLiteral(l.getLexicalForm(), dt);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static RDFNode toJena(Term t) {
        if (t == null) return null;
        else if (t instanceof Lit) return toJena((Lit)t);
        else if (t instanceof Res) return toJena((Res)t);
        else
            throw new UnsupportedOperationException("Cannot convert "+t.getClass()+" to RDFNode");
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Property toJenaProperty(Term t) {
        if (t == null) return null;
        else if (!(t instanceof URI))
            throw new UnsupportedOperationException("Cannot convert non-URI to jena Property");
        return ResourceFactory.createProperty(((URI)t).getURI());
    }

    /* ~~~~~~~~~ toJenaNode(Term) ~~~~~~~~~ */

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Node_Variable toJenaNode(Var var) {
        if (var == null) return null;
        else if (var instanceof JenaBaseTerm)
            return (Node_Variable) ((JenaBaseTerm) var).getGraphNode();
        return (Node_Variable) NodeFactory.createVariable(var.getName());
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Node_URI toJenaNode(URI term) {
        if (term == null) return null;
        else if (term instanceof JenaBaseTerm)
            return (Node_URI) ((JenaBaseTerm) term).getGraphNode();
        return  (Node_URI) NodeFactory.createURI(term.getURI());
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Node_Literal toJenaNode(Lit term) {
        if (term == null) return null;
        else if (term instanceof JenaBaseTerm)
            return (Node_Literal) ((JenaBaseTerm) term).getGraphNode();

        String langTag = term.getLangTag();
        if (langTag != null)
            return (Node_Literal) NodeFactory.createLiteral(term.getLexicalForm(), langTag);

        String dtURI = term.getDatatype().getURI();
        RDFDatatype datatype = getInstance().getTypeByName(dtURI);
        if (datatype == null) {
            throw new IllegalArgumentException("Cannot convert "+term+" to Jena Node, " +
                                               "since datatype "+dtURI+" is unknown to Jena");
        }
        return (Node_Literal) NodeFactory.createLiteral(term.getLexicalForm(), datatype);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Node toJenaNode(Term term) {
        if (term == null) return null;
        else if (term instanceof JenaBaseTerm) return ((JenaBaseTerm)term).getGraphNode();
        else if (term.isVar()) return toJenaNode(term.asVar());
        else if (term.isURI()) return toJenaNode(term.asURI());
        else if (term.isLiteral()) return toJenaNode(term.asLiteral());

        String msg = "Cannot convert Term of class " + term.getClass() + " to Jena";
        throw new UnsupportedOperationException(msg);
    }

    /* ~~~~~~~~~ fromJena(Triple) fromJena(Statement) ~~~~~~~~~ */

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Triple fromJena(org.apache.jena.graph.Triple t) {
        if (t == null) return null;
        JenaNodeTerm s = fromJena(t.getSubject());
        JenaNodeTerm p = fromJena(t.getPredicate());
        JenaNodeTerm o = fromJena(t.getObject());
        return new Triple(s, p, o);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static Triple fromJena(Statement statement) {
        if (statement == null) return null;
        JenaRes s = fromJena(statement.getSubject());
        JenaRes p = fromJena(statement.getPredicate());
        JenaTerm o = fromJena(statement.getObject());
        return new Triple(s, p, o);
    }

    /* ~~~~~~~~~ fromJena(PrefixMapping) ~~~~~~~~~ */

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static PrefixMappingDict fromJena(PrefixMapping mapping) {
        return mapping == null ? null : new PrefixMappingDict(mapping);
    }

    @Contract(value = "null -> null; !null -> new", pure = true)
    public static PrefixMapping toJena(PrefixDict dict) {
        if (dict == null) return null;
        PrefixMappingImpl m = new PrefixMappingImpl();
        for (Map.Entry<String, String> entry : dict.entries())
            m.setNsPrefix(entry.getKey(), entry.getValue());
        return m;
    }
}
