package br.ufsc.lapesd.freqel.reason.tbox;

import br.ufsc.lapesd.freqel.jena.JenaWrappers;
import br.ufsc.lapesd.freqel.jena.ModelUtils;
import br.ufsc.lapesd.freqel.jena.model.term.JenaTerm;
import br.ufsc.lapesd.freqel.model.term.Blank;
import br.ufsc.lapesd.freqel.model.term.Res;
import br.ufsc.lapesd.freqel.model.term.Term;
import com.google.common.base.Preconditions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * A Simple reasoner that only processes the transitivity of subClassOf/subPropertyOf properties.
 */
public class TransitiveClosureTBoxReasoner implements TBoxReasoner {
    private @Nullable Model model;

    public TransitiveClosureTBoxReasoner() { }

    public TransitiveClosureTBoxReasoner(@Nonnull TBoxSpec spec) throws TBoxLoadException {
        load(spec);
    }

    @Override
    public void load(@Nonnull TBoxSpec sources) {
        model = sources.loadModel();
    }

    private @Nonnull Stream<Term> transitiveClosure(@Nonnull Term start,
                                                    @Nonnull Property property) {
        Preconditions.checkArgument(start instanceof Res, "Term start must be a instance of Res");
        if (model == null)
            return Stream.empty();
        Resource resource;
        if (start instanceof JenaTerm)
            resource = ((JenaTerm) start).getModelNode().asResource();
        else if (start instanceof Blank)
            return Stream.empty();
        else
            resource = model.createResource(start.asURI().getURI());
        return ModelUtils.closure(model, resource, property, true, true)
                .map(JenaWrappers::fromJena);
    }

    @Override
    public @Nonnull Stream<Term> subClasses(@Nonnull Term term) {
        return transitiveClosure(term, RDFS.subClassOf);
    }

    @Override
    public @Nonnull Stream<Term> subProperties(@Nonnull Term term) {
        return transitiveClosure(term, RDFS.subPropertyOf);
    }

    @Override
    public void close() {
        model = null;
    }
}