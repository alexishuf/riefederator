package br.ufsc.lapesd.freqel.query;

import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.model.term.Term;
import br.ufsc.lapesd.freqel.model.term.std.StdVar;
import br.ufsc.lapesd.freqel.model.term.std.TemplateLink;
import com.google.errorprone.annotations.CheckReturnValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateExpander implements Function<CQuery, CQuery> {
    private static final @Nonnull Pattern TPL_RX = Pattern.compile("tpl(\\d+)");
    public static final @Nonnull TemplateExpander INSTANCE = new TemplateExpander();

    private final Map<String, TemplateLink> registry = new HashMap<>();

    public TemplateExpander register(@Nonnull TemplateLink link) {
        registry.put(link.getURI(), link);
        return this;
    }

    @Override
    public @Nonnull CQuery apply(@Nonnull CQuery query) {
        return apply(query, null);
    }

    private boolean hasTemplatePredicate(@Nonnull Triple triple) {
        Term predicate = triple.getPredicate();
        if (predicate instanceof TemplateLink)
            return true;
        return predicate.isURI() && registry.containsKey(predicate.asURI().getURI());
    }

    public @Nonnull CQuery apply(@Nonnull CQuery query, @Nullable int[] lastId) {
        if (query.stream().noneMatch(this::hasTemplatePredicate))
            return query; // no template
        if (lastId == null)
            lastId = new int[]{getLastId(query)};
        MutableCQuery result = new MutableCQuery(query.size() + 10);
        Map<Term, Term> var2Safe = new HashMap<>();
        for (Triple triple : query) {
            Term predicate = triple.getPredicate();
            if (!(predicate instanceof TemplateLink) && predicate.isURI()) {
                TemplateLink template = registry.get(predicate.asURI().getURI());
                if (template != null)
                    triple = triple.withPredicate(template);
            }
            expandTo(query, result, lastId, var2Safe, triple);
            var2Safe.clear();
        }
        // copy annotations for terms in query triple annotations are copied in expandTo
        result.copyTermAnnotations(query);
        return result;
    }

    private void expandTo(@Nonnull CQuery inQuery, @Nonnull MutableCQuery out,
                          @Nonnull int[] lastId, @Nonnull Map<Term, Term> var2Safe,
                          @Nonnull Triple input) {
        assert var2Safe.isEmpty();
        if (!(input.getPredicate() instanceof TemplateLink)) {
            out.add(input);
            inQuery.getTripleAnnotations(input).forEach(a -> out.annotate(input, a));
            return;
        }
        TemplateLink templateLink = (TemplateLink) input.getPredicate();
        CQuery tplQuery = apply(templateLink.getTemplate(), lastId);
        for (Triple triple : tplQuery) {
            Term s = getCanon(lastId, var2Safe, triple.getSubject(), templateLink, input);
            Term o = getCanon(lastId, var2Safe, triple.getObject() , templateLink, input);
            Triple rewritten = new Triple(s, triple.getPredicate(), o);

            // add triple and triple annotations (from tplQuery)
            out.add(rewritten);
            tplQuery.getTripleAnnotations(triple).forEach(a -> out.annotate(rewritten, a));
        }
        //transfer term annotations from tplQuery
        for (Map.Entry<Term, Term> e : var2Safe.entrySet())
            out.annotate(e.getValue(), tplQuery.getTermAnnotations(e.getKey()));
    }

    private @Nonnull Term getCanon(@Nonnull int[] lastId, @Nonnull Map<Term, Term> var2Id,
                                   @Nonnull Term tplTerm,
                                   @Nonnull TemplateLink tpl, @Nonnull Triple input) {
        if (!tplTerm.isVar()) return tplTerm;
        if (tplTerm.equals(tpl.getSubject())) return input.getSubject();
        if (tplTerm.equals(tpl.getObject())) return input.getObject();
        return var2Id.computeIfAbsent(tplTerm, x -> new StdVar("tpl" + ++lastId[0]));
    }

    private @Nonnull Integer getLastId(@Nonnull CQuery query) {
        return query.attr().allVarNames().stream().map(n -> {
            Matcher matcher = TPL_RX.matcher(n);
            if (!matcher.matches()) return null;
            return Integer.parseInt(matcher.group(1));
        }).filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
    }

    @CheckReturnValue
    public static @Nonnull CQuery expandTemplates(@Nonnull CQuery query) {
        return INSTANCE.apply(query);
    }
}
