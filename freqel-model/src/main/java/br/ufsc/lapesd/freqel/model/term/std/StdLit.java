package br.ufsc.lapesd.freqel.model.term.std;

import br.ufsc.lapesd.freqel.V;
import br.ufsc.lapesd.freqel.model.RDFUtils;
import br.ufsc.lapesd.freqel.model.term.AbstractLit;
import br.ufsc.lapesd.freqel.model.term.URI;
import com.google.errorprone.annotations.Immutable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("WeakerAccess")
@Immutable
public class StdLit extends AbstractLit {
    private final @Nullable String lang;
    private final @Nonnull URI dt;
    private final @Nonnull String lexical;
    private final boolean escaped;
    private static final @Nonnull StdURI xsdString = V.XSD.xstring;
    private static final @Nonnull StdURI rdfLangString = V.RDF.langString;

    private StdLit(@Nonnull String lexical, boolean escaped, @Nonnull URI dt, @Nullable String lang) {
        assert lang == null ^ dt.equals(rdfLangString);
        this.dt = dt;
        this.lang = lang;
        this.lexical = lexical;
        this.escaped = escaped;
    }

    public static StdLit fromEscaped(@Nonnull String lexical, @Nonnull URI dt) {
        return new StdLit(lexical, true, dt, null);
    }
    public static StdLit fromEscaped(@Nonnull String lexical) {
        return fromEscaped(lexical, xsdString);
    }
    public static StdLit fromUnescaped(@Nonnull String lexical, @Nonnull URI dt) {
        return new StdLit(lexical, false, dt, null);
    }
    public static StdLit fromUnescaped(@Nonnull String lexical) {
        return fromUnescaped(lexical, xsdString);
    }
    public static StdLit fromEscaped(@Nonnull String lexical, @Nonnull String lang) {
        return new StdLit(lexical, true, rdfLangString, lang);
    }
    public static StdLit fromUnescaped(@Nonnull String lexical, @Nonnull String lang) {
        return new StdLit(lexical, false, rdfLangString, lang);
    }

    @Override
    public @Nonnull String getLexicalForm() {
        return escaped ? RDFUtils.unescapeLexicalForm(lexical) : lexical;
    }

    public @Nonnull String getEscapedLexicalForm() {
        return escaped ? lexical : RDFUtils.escapeLexicalForm(lexical);
    }

    @Override
    public @Nonnull URI getDatatype() {
        return dt;
    }

    @Override
    public @Nullable String getLangTag() {
        return lang;
    }
}
