package br.ufsc.lapesd.riefederator.webapis.requests.impl;

import br.ufsc.lapesd.riefederator.model.term.Lit;
import br.ufsc.lapesd.riefederator.model.term.Term;
import br.ufsc.lapesd.riefederator.model.term.URI;
import br.ufsc.lapesd.riefederator.webapis.requests.APIRequestExecutor;
import br.ufsc.lapesd.riefederator.webapis.requests.NoTermSerializationException;
import br.ufsc.lapesd.riefederator.webapis.requests.TermSerializer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static java.lang.String.format;

public class SimpleTermSerializer implements TermSerializer {
    public static final @Nonnull SimpleTermSerializer INSTANCE = new SimpleTermSerializer();

    @Override
    public @Nonnull String toString(@Nonnull Term term, @Nullable String paramName,
                                    @Nullable APIRequestExecutor executor)
            throws NoTermSerializationException {

        if (term instanceof Lit) {
            return ((Lit) term).getLexicalForm();
        } else if (term instanceof URI) {
            return ((URI) term).getURI();
        } else {
            String msg = format("Cannot bind %s into %s in %s", term, paramName, executor);
            throw new NoTermSerializationException(term, msg);
        }
    }
}
