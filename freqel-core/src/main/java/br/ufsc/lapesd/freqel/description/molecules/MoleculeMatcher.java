package br.ufsc.lapesd.freqel.description.molecules;

import br.ufsc.lapesd.freqel.description.CQueryMatch;
import br.ufsc.lapesd.freqel.description.MatchReasoning;
import br.ufsc.lapesd.freqel.description.molecules.annotations.AtomAnnotation;
import br.ufsc.lapesd.freqel.description.semantic.AlternativesSemanticDescription;
import br.ufsc.lapesd.freqel.description.semantic.SemanticCQueryMatch;
import br.ufsc.lapesd.freqel.jena.query.modifiers.filter.JenaSPARQLFilter;
import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.model.term.Term;
import br.ufsc.lapesd.freqel.model.term.Var;
import br.ufsc.lapesd.freqel.query.CQuery;
import br.ufsc.lapesd.freqel.query.MutableCQuery;
import br.ufsc.lapesd.freqel.query.annotations.MatchAnnotation;
import br.ufsc.lapesd.freqel.query.annotations.MergePolicyAnnotation;
import br.ufsc.lapesd.freqel.query.annotations.NoMergePolicyAnnotation;
import br.ufsc.lapesd.freqel.query.modifiers.filter.SPARQLFilter;
import br.ufsc.lapesd.freqel.reason.tbox.TBox;
import br.ufsc.lapesd.freqel.util.CollectionUtils;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.apache.commons.lang3.tuple.ImmutablePair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class MoleculeMatcher implements AlternativesSemanticDescription {
    private final @Nonnull Molecule molecule;
    private final @Nonnull TBox reasoner;
    private final @Nonnull MergePolicyAnnotation mergePolicyAnnotation;
    private @Nonnull SoftReference<Index> index = new SoftReference<>(null);

    public MoleculeMatcher(@Nonnull Molecule molecule, @Nonnull TBox reasoner) {
        this(molecule, reasoner, new NoMergePolicyAnnotation());
    }

    public MoleculeMatcher(@Nonnull Molecule molecule, @Nonnull TBox reasoner,
                           @Nonnull MergePolicyAnnotation mergePolicy) {
        this.molecule = molecule;
        this.reasoner = reasoner;
        this.mergePolicyAnnotation = mergePolicy;
    }

    public @Nonnull TBox getReasoner() {
        return reasoner;
    }

    public @Nonnull Molecule getMolecule() {
        return molecule;
    }

    @Override
    public @Nonnull CQueryMatch match(@Nonnull CQuery query, @Nonnull MatchReasoning reasoning) {
        boolean altReason = reasoning == MatchReasoning.ALTERNATIVES;
        return createState(query, altReason).matchExclusive().matchNonExclusive().build();
    }

    @Override public @Nonnull CQueryMatch localMatch(@Nonnull CQuery query,
                                                      @Nonnull MatchReasoning reasoning) {
        return match(query, reasoning);
    }

    @Override public boolean supports(@Nonnull MatchReasoning mode) {
        return MatchReasoning.NONE.equals(mode) || MatchReasoning.ALTERNATIVES.equals(mode);
    }

    protected @Nonnull MoleculeMatcher.State createState(@Nonnull CQuery query, boolean reasoning) {
        return new State(query, reasoning);
    }

    @Override
    public @Nonnull SemanticCQueryMatch semanticMatch(@Nonnull CQuery query) {
        return createState(query, true).matchExclusive().matchNonExclusive().build();
    }

    @Override
    public void update() {
        /* no op */
    }

    @Override
    public void init() {
        /* no op */
    }

    @Override
    public boolean waitForInit(int timeoutMilliseconds) {
        return true; /* alway initialized */
    }

    @Override
    public boolean updateSync(int timeoutMilliseconds) {
        return true; //no op
    }


    @Override
    public @Nonnull String toString() {
        return String.format("MoleculeMatcher(%s)", molecule);
    }

    private @Nonnull Index getIndex() {
        Index strong = this.index.get();
        if (strong == null) index = new SoftReference<>(strong = new Index());
        return strong;
    }

    protected final static class Link {
        public @Nonnull Atom s, o;
        public @Nonnull Term p;
        public @Nonnull MoleculeLink link;
        public final boolean reversed;
        private int hash = 0;

        public Link(@Nonnull Atom s, @Nonnull MoleculeLink link, @Nonnull Atom o,
                    boolean reversed) {
            this.s = s;
            this.link = link;
            this.p = link.getEdge();
            this.o = o;
            this.reversed = reversed;
        }

        @Override
        public boolean equals(Object o1) {
            if (this == o1) return true;
            if (o1 == null || getClass() != o1.getClass()) return false;
            Link other = (Link) o1;
            return s.equals(other.s) && p.equals(other.p) && o.equals(other.o);
        }

        @Override
        public String toString() {
            return String.format("(%s %s %s)", s, p, o);
        }

        @Override
        public int hashCode() {
            if (hash == 0)
                hash = Objects.hash(s, o, p, hash);
            return hash;
        }
    }

    protected final static class LinkMatch {
        public @Nonnull Link l;
        public @Nonnull ImmutablePair<Term, Atom> from;
        public @Nonnull Triple triple;

        public LinkMatch(@Nonnull Link l, @Nonnull ImmutablePair<Term, Atom> from,
                         @Nonnull Triple triple) {
            this.l = l;
            this.from = from;
            this.triple = triple;
        }

        public @Nonnull ImmutablePair<Term, Atom> getTo() {
            String atomName = from.right.getName();
            if (l.s.getName().equals(atomName) && triple.getSubject().equals(from.left))
                return ImmutablePair.of(triple.getObject(), l.o);
            else
                return ImmutablePair.of(triple.getSubject(), l.s);
        }

        public boolean sameMatch(@Nonnull LinkMatch other) {
            return l.equals(other.l) && triple.equals(other.triple);
        }

        @Override
        public @Nonnull String toString() {
            return String.format("LinkMatch(%s, %s, %s)", l, from, triple);
        }
    }

    private class Index {
        private final @Nonnull Map<Term, SetMultimap<String, Link>> map;
        private final @Nonnull Multimap<Term, Link> pred2link;
        private final @Nonnull Multimap<String, Link> subj, obj;
        private final @Nonnull List<Atom> exclusive;
        private final @Nonnull LoadingCache<Term, List<Term>> predicatesInIndex;

        private final int atomCount;

        private Index() {
            atomCount = molecule.getAtomCount();
            int capacity = atomCount * 8;
            map = new HashMap<>(capacity);
            subj = MultimapBuilder.hashKeys(atomCount).hashSetValues().build();
            obj = MultimapBuilder.hashKeys(atomCount).hashSetValues().build();
            pred2link = MultimapBuilder.hashKeys(capacity).arrayListValues().build();
            exclusive = new ArrayList<>(atomCount);
            predicatesInIndex = CacheBuilder.newBuilder().build(new CacheLoader<Term, List<Term>>() {
                @Override
                public List<Term> load(@Nonnull Term key) {
                    return loadIndexedSubProperties(key);
                }
            });

            Queue<Atom> queue = new ArrayDeque<>(molecule.getCores());
            HashSet<String> visited = new HashSet<>();
            while (!queue.isEmpty()) {
                Atom a = queue.remove();
                if (!visited.add(a.getName()))
                    continue;
                if (a.isExclusive())
                    exclusive.add(a);
                for (MoleculeLink l : a.getIn()) {
                    queue.add(l.getAtom());
                    Link link = new Link(l.getAtom(), l, a, true);
                    if (!a.isExclusive())
                        pred2link.put(l.getEdge(), link);
                    SetMultimap<String, Link> a2l = getAtom2Link(l.getEdge());
                    a2l.put(a.getName(), link);
                    a2l.put(l.getAtom().getName(), link);
                    subj.put(a.getName(), link);
                }
                for (MoleculeLink l : a.getOut()) {
                    queue.add(l.getAtom());
                    Link link = new Link(a, l, l.getAtom(), false);
                    if (!a.isExclusive())
                        pred2link.put(l.getEdge(), link);
                    SetMultimap<String, Link> a2l = getAtom2Link(l.getEdge());
                    a2l.put(a.getName(), link);
                    a2l.put(l.getAtom().getName(), link);
                    obj.put(a.getName(), link);
                }
            }
        }

        private List<Term> loadIndexedSubProperties(@Nonnull Term predicate)  {
            Preconditions.checkArgument(predicate.isGround());
            return Stream.concat(reasoner.subProperties(predicate), Stream.of(predicate))
                    .filter(p -> map.containsKey(p) || pred2link.containsKey(p)).collect(toList());
        }

        private @Nonnull SetMultimap<String, Link> getAtom2Link(@Nonnull Term predicate) {
            return map.computeIfAbsent(predicate, k -> MultimapBuilder.hashKeys(atomCount)
                                                                      .hashSetValues().build());
        }

        @Nonnull List<Atom> getExclusive() {
            return exclusive;
        }

        @Nonnull Stream<Link> streamNE(@Nonnull Term predicate, boolean reason) {
            Preconditions.checkArgument(predicate.isGround());
            if (!reason)
                return pred2link.get(predicate).stream();
            try {
                return predicatesInIndex.get(predicate).stream()
                        .flatMap(p -> pred2link.get(p).stream());
            } catch (ExecutionException e) { // should never throw
                throw new RuntimeException(e);
            }
        }

        @Nonnull Stream<Link> stream(@Nonnull Term predicate, @Nonnull Atom atom,
                                     @Nullable Triple.Position atomPosition,
                                     boolean reason) {
            if (atomPosition == Triple.Position.PRED)
                return Stream.empty();
            String name = atom.getName();
            if (predicate.isVar()) {
                if      (atomPosition == Triple.Position.SUBJ) return subj.get(name).stream();
                else if (atomPosition == Triple.Position.OBJ)  return  obj.get(name).stream();
                return Stream.concat(subj.get(name).stream(), obj.get(name).stream());
            }
            Stream<Link> stream;
            if (!reason) {
                SetMultimap<String, Link> mMap = map.get(predicate);
                stream = mMap == null ? Stream.empty() : mMap.get(name).stream();
            } else {
                try {
                    stream = predicatesInIndex.get(predicate).stream()
                                              .flatMap(p -> map.get(p).get(name).stream());
                } catch (ExecutionException e) {
                    throw new RuntimeException(e); // should never throw
                }
            }
            if (atomPosition == Triple.Position.SUBJ)
                return stream.filter(l -> l.s.getName().equals(name));
            else if (atomPosition == Triple.Position.OBJ)
                return stream.filter(l -> l.o.getName().equals(name));
            return stream.filter(l -> l.s.getName().equals(name) || l.o.getName().equals(name));
        }

        public boolean hasPredicate(@Nonnull Term predicate, boolean reason) {
            if (predicate.isVar())
                return true;
            if (reason) {
                try {
                    return !predicatesInIndex.get(predicate).isEmpty();
                } catch (ExecutionException e) {
                    throw new RuntimeException(e); // never throws
                }
            } else {
                return map.containsKey(predicate) || pred2link.containsKey(predicate);
            }
        }
    }

    protected class State {
        protected  @Nonnull final CQuery parentQuery;
        protected boolean reason;
        protected  @Nonnull Map<Term, CQuery> subQueries;
        protected  @Nonnull Map<ImmutablePair<Term, Atom>, List<List<LinkMatch>>> visited;
        protected  @Nonnull Multimap<ImmutablePair<Term, Atom>, LinkMatch>  incoming;
        protected  @Nullable SemanticCQueryMatch.Builder matchBuilder = null;
        protected  @Nonnull Index idx;

        public State(@Nonnull CQuery query, boolean reason) {
            this.parentQuery = query;
            this.reason = reason;
            this.idx = getIndex();
            int count = query.size();
            HashSet<Term> sos = Sets.newHashSetWithExpectedSize(count * 2);
            for (Triple t : query) {
                sos.add(t.getSubject());
                sos.add(t.getObject());
            }
            subQueries = Maps.newHashMapWithExpectedSize(sos.size());
            List<Triple.Position> positions = asList(Triple.Position.SUBJ, Triple.Position.OBJ);
            for (Term term : sos)
                subQueries.put(term, query.containing(term, positions));
            int atomCount = molecule.getAtomCount();
            visited = new HashMap<>(count*atomCount);
            incoming = MultimapBuilder.hashKeys(count*atomCount)
                                      .arrayListValues().build();
        }

        public @Nonnull SemanticCQueryMatch.Builder matchBuilder() {
            if (matchBuilder == null)
                matchBuilder = SemanticCQueryMatch.builder(parentQuery);
            return matchBuilder;
        }

        public @Nonnull SemanticCQueryMatch build() {
            return matchBuilder == null ? SemanticCQueryMatch.EMPTY : matchBuilder.build();
        }

        protected boolean ignoreTriple(@Nonnull Triple t) {
            return false;
        }

        public @Nonnull State matchNonExclusive() {
            for (Triple t : parentQuery) {
                if (ignoreTriple(t)) continue;
                if (t.getPredicate().isVar()) {
                    matchBuilder().addTriple(t).addAlternative(t, t);
                } else {
                    Iterator<Link> it = idx.streamNE(t.getPredicate(), reason).iterator();
                    if (it.hasNext())
                        matchBuilder().addTriple(t);
                    while (it.hasNext())
                        matchBuilder().addAlternative(t, t.withPredicate(it.next().p));
                }
            }
            return this;
        }

        public @Nonnull State matchExclusive() {
            Index idx = getIndex();
            // try all term - atom combinations.
            for (Map.Entry<Term, CQuery> entry : subQueries.entrySet()) {
                for (Atom atom : idx.getExclusive()) {
                    ImmutablePair<Term, Atom> termAtom = ImmutablePair.of(entry.getKey(), atom);
                    List<List<LinkMatch>> linkLists = findLinks(entry.getValue(), termAtom);
                    if (linkLists == null)
                        continue; // unsatisfiable
                    visited.put(termAtom, linkLists);
                    for (List<LinkMatch> list : linkLists) {
                        for (LinkMatch match : list)  incoming.put(match.getTo(), match);
                    }
                }
            }
            cascadeEliminations();
            // save remaining exclusive groups
            for (EGPrototype egPrototype : mergeIntersecting())
                saveExclusiveGroup(egPrototype.query, egPrototype.matchLists);
            return this;
        }

        private void cascadeEliminations() {
            Queue<ImmutablePair<Term, Atom>> queue = new ArrayDeque<>();
            for (Map.Entry<ImmutablePair<Term, Atom>, List<List<LinkMatch>>> e : visited.entrySet()) {
                if (e.getValue() != null) continue;
                for (LinkMatch linkMatch : incoming.get(e.getKey())) queue.add(linkMatch.from);
            }
            while (!queue.isEmpty()) {
                ImmutablePair<Term, Atom> pair = queue.remove();
                if (visited.remove(pair) == null)
                    continue; // was previously eliminated
                for (LinkMatch match : incoming.get(pair)) queue.add(match.from);
            }
        }

        private List<EGPrototype> mergeIntersecting() {
            List<EGPrototype> list = new ArrayList<>(visited.size());
            for (Map.Entry<ImmutablePair<Term, Atom>, List<List<LinkMatch>>> e : visited.entrySet()) {
                if (e.getValue() != null) {
                    EGPrototype eg = new EGPrototype(subQueries.get(e.getKey().left), e.getValue());
                    list.add(eg);
                }
            }
            for (int i = 0; i < list.size(); i++) {
                for (int j = i+1; j < list.size(); j++) {
                    EGPrototype merge = tryMerge(list.get(i), list.get(j));
                    if (merge != null) {
                        list.remove(j);
                        // will be i+1 on next iteration
                        // We need to start over because for i < k < j, list[k] may share triples
                        // with list[j]. This could make a previous failed tryMerge(i, k) succeed
                        // now that list[i] is "bigger"
                        j = i;
                        list.set(i, merge);
                    }
                }
            }
            // remove non-executable EGs
            list.removeIf(eg -> !isValidEG(eg.query, eg.matchLists));
            return list;
        }


        @SuppressWarnings("ReferenceEquality")
        private @Nullable EGPrototype tryMerge(@Nonnull EGPrototype l, @Nonnull EGPrototype r) {
            Set<Triple> commonTriples = CollectionUtils.intersect(l.query.attr().getSet(),
                                                            r.query.attr().getSet());
            if (commonTriples.isEmpty())
                return null; //no intersection
            CQuery union = CQuery.merge(l.query, r.query);
            List<List<LinkMatch>> matchLists = new ArrayList<>(union.size());
            Set<Link> lLinks = new HashSet<>(), rLinks = new HashSet<>();
            SetMultimap<Term, Triple> p2triple = HashMultimap.create(union.size(), 2);
            for (Triple triple : union) {
                if (commonTriples.contains(triple)) {
                    lLinks.clear();
                    rLinks.clear();
                    int lIndex = l.query.indexOf(triple);
                    l.matchLists.get(lIndex                 ).forEach(m -> lLinks.add(m.l));
                    r.matchLists.get(r.query.indexOf(triple)).forEach(m -> rLinks.add(m.l));
                    if (!lLinks.equals(rLinks) || lLinks.isEmpty())
                        return null;
                    matchLists.add(l.matchLists.get(lIndex));
                } else if (l.query.contains(triple)) {
                    matchLists.add(l.matchLists.get(l.query.indexOf(triple)));
                } else if (r.query.contains(triple)) {
                    matchLists.add(r.matchLists.get(r.query.indexOf(triple)));
                }
                p2triple.put(triple.getPredicate(), triple);
            }
            for (Term predicate : p2triple.keySet()) {
                Set<Triple> set = p2triple.get(predicate);
                for (Triple i : set) {
                    Term s = i.getSubject(), o = i.getObject();
                    for (Triple j : set) {
                        if (i == j) continue;
                        if (s.equals(j.getSubject()) || o.equals(j.getObject()))
                            return null; //proceeding with the merge will likely break joins
                    }
                }
            }
            if (isAmbiguousEG(union, matchLists))
                return null;
            return new EGPrototype(union, matchLists);
        }

        protected class EGPrototype {
            public CQuery query;
            public List<List<LinkMatch>> matchLists;

            public EGPrototype(CQuery query, List<List<LinkMatch>> matchLists) {
                this.query = query;
                this.matchLists = matchLists;
            }

            @Override
            public @Nonnull String toString() {
                StringBuilder builder = new StringBuilder();
                builder.append("EGPrototype{\n").append(query).append("\n[\n");
                for (List<LinkMatch> list : matchLists)
                    builder.append("  ").append(list).append("\n");
                return builder.append("]").toString();
            }
        }

        /**
         * This allows a subclass to verify if a EG is valid.
         *
         * This method is called after there are no more expectations to merge and enlarge EG's.
         * So this is the place to verify if there are enough inputs.
         *
         * @param query all triples in the EG. not yeat annotated with {@link AtomAnnotation}
         * @param matchLists A list of {@link LinkMatch} for each triple in query, in the same order
         * @return true iff valid
         */
        protected boolean isValidEG(CQuery query, List<List<LinkMatch>> matchLists) {
            return true;
        }

        /**
         * This allows a subclass to verify if a EG or an EG merge introduces ambiguity.
         *
         * @param query all triples in the EG. not yeat annotated with {@link AtomAnnotation}
         * @param matchLists A list of {@link LinkMatch} for each triple in query, in the same order
         * @return true iff ambiguous
         */
        protected boolean isAmbiguousEG(CQuery query, List<List<LinkMatch>> matchLists) {
            return false;
        }

        @Nullable List<List<LinkMatch>> findLinks(@Nonnull CQuery query,
                                                  @Nonnull ImmutablePair<Term, Atom> termAtom) {
            Atom atom = termAtom.right;
            ArrayList<List<LinkMatch>> linkLists = new ArrayList<>(query.size());
            boolean empty = true;
            for (Triple triple : query) {
                if (ignoreTriple(triple)) continue;
                Triple.Position pos = triple.where(termAtom.left);
                assert pos != null;
                ArrayList<LinkMatch> found = new ArrayList<>();
                idx.stream(triple.getPredicate(), atom, pos, reason)
                        .forEach(l -> found.add(new LinkMatch(l, termAtom, triple)));
                if (atom.isClosed() && found.isEmpty())
                    return null;
                linkLists.add(found);
                empty &= found.isEmpty();
            }
            if (empty)
                return null;
            if (linkLists.size() == 1 && linkLists.get(0).isEmpty())
                return null;
            return linkLists;
        }

        protected class EGQueryBuilder {
            protected CQuery matchedEG = null;
            protected MutableCQuery mQuery;
            protected Set<Var> allVars = new HashSet<>();
            protected SetMultimap<Term, String> term2atom = HashMultimap.create();
            protected Map<JenaSPARQLFilter.SubsumptionResult, AtomFilter> subsumption2matched
                    = new HashMap<>();

            public EGQueryBuilder(int sizeHint) {
                this.mQuery = new MutableCQuery(sizeHint);
            }

            /**
             * Creates a buidler for an alternative (reasoning) query
             */
            public EGQueryBuilder(@Nonnull CQuery matchedEG,
                                  @Nonnull EGQueryBuilder matchedBuilder) {
                this.mQuery = new MutableCQuery(matchedEG.size());
                this.matchedEG = matchedEG;
                term2atom.putAll(matchedBuilder.term2atom);
                subsumption2matched.putAll(matchedBuilder.subsumption2matched);
            }

            public void add(@Nonnull Triple triple, @Nonnull Collection<LinkMatch> matches) {
                assert parentQuery.contains(triple);
                triple.forEach(t -> {
                    if (t.isVar()) allVars.add(t.asVar());
                });
                mQuery.add(triple);
                parentQuery.getTripleAnnotations(triple).forEach(a -> mQuery.annotate(triple, a));
                Term s = triple.getSubject(), o = triple.getObject();
                if (!molecule.getFilters().isEmpty()) {
                    for (LinkMatch match : matches) {
                        term2atom.put(s, match.l.s.getName());
                        term2atom.put(o, match.l.o.getName());
                    }
                }
                addTripleAnnotations(triple, matches);
                addAtomAnnotations(triple, matches);
            }

            private void addTripleAnnotations(@Nonnull Triple triple,
                                              @Nonnull Collection<LinkMatch> matches) {
                for (LinkMatch m : matches)
                    mQuery.annotate(triple, new MoleculeLinkAnnotation(m.l.link, m.l.reversed));
            }

            protected void addAtomAnnotations(@Nonnull Triple triple,
                                              @Nonnull Collection<LinkMatch> matches) {
                Term s = triple.getSubject(), o = triple.getObject();
                for (LinkMatch match : matches) {
                    mQuery.annotate(s, AtomAnnotation.of(match.l.s));
                    mQuery.annotate(o, AtomAnnotation.of(match.l.o));
                }
            }

            @CanIgnoreReturnValue
            public boolean tryAdd(@Nonnull SPARQLFilter filter) {
                if (!allVars.containsAll(filter.getVars()))
                    return false;
                Set<AtomFilter> candidates = null;
                for (Var var : filter.getVars()) {
                    Set<AtomFilter> set = term2atom.get(var).stream()
                            .flatMap(a -> molecule.getFiltersWithAtom(a).stream()).collect(toSet());
                    if (candidates == null) {
                        candidates = set;
                    } else {
                        if (candidates.isEmpty())
                            return false;
                        candidates = CollectionUtils.intersect(candidates, set);
                    }
                }
                if (candidates == null) candidates = Collections.emptySet();
                for (AtomFilter candidate : candidates) {
                    JenaSPARQLFilter.SubsumptionResult result;
                    result = filter.areResultsSubsumedBy(candidate.getSPARQLFilter());
                    if (result.getValue()) {
                        mQuery.mutateModifiers().add(filter);
                        subsumption2matched.put(result, candidate);
                        return true;
                    }
                }
                return false;
            }

            public void addAlternative(@Nonnull CQuery query, @Nonnull Triple triple,
                                       @Nonnull Triple alt) {
                assert parentQuery.contains(triple);
                alt.forEach(t -> {
                    if (t.isVar()) allVars.add(t.asVar());
                });
                mQuery.add(alt);
                parentQuery.getTripleAnnotations(triple).forEach(a -> mQuery.annotate(alt, a));
                query.getTripleAnnotations(triple).forEach(a -> mQuery.annotate(alt, a));
                if (!alt.equals(triple))
                    mQuery.annotate(alt, new MatchAnnotation(triple));
            }

            public boolean isEmpty() {
                return mQuery.isEmpty();
            }

            public int size() {
                return mQuery.size();
            }

            public void addParentModifiers() {
                parentQuery.getModifiers().filters().forEach(this::tryAdd);
            }

            public CQuery build() {
                if (matchedEG != null) {
                    mQuery.copyTermAnnotations(matchedEG);
                    mQuery.copyTripleAnnotations(matchedEG);
                }
                mQuery.copyTermAnnotations(parentQuery);
                mQuery.annotate(mergePolicyAnnotation);
                return mQuery;
            }
        }

        protected @Nonnull EGQueryBuilder createEGQueryBuilder(int sizeHint) {
            return new EGQueryBuilder(sizeHint);
        }
        protected @Nonnull EGQueryBuilder
        createEGQueryBuilder(@Nonnull CQuery matched, @Nonnull EGQueryBuilder matchedBuilder) {
            return new EGQueryBuilder(matched, matchedBuilder);
        }

        boolean hasAlternatives(@Nonnull Triple queryTriple, @Nonnull List<LinkMatch> linkMatches) {
            int size = linkMatches.size();
            assert size > 0;
            Term p = queryTriple.getPredicate();
            return p.isGround() && ( size > 1 || !p.equals(linkMatches.get(0).l.p) );
        }

        void saveExclusiveGroup(@Nonnull CQuery query, @Nonnull List<List<LinkMatch>> linkLists) {
            ArrayList<List<Term>> predicatesList = new ArrayList<>();
            EGQueryBuilder subQuery = createEGQueryBuilder(query.size());
            HashSet<Term> temp = new HashSet<>();
            Iterator<Triple> queryIt = query.iterator();
            boolean hasAlternatives = false;
            for (List<LinkMatch> list : linkLists) {
                Triple triple = queryIt.next();
                if (list.isEmpty())
                    continue;
                if (hasAlternatives(triple, list)) {
                    hasAlternatives = true;
                    temp.clear();
                    for (LinkMatch linkMatch : list) temp.add(linkMatch.l.p);
                    predicatesList.add(new ArrayList<>(temp));
                } else {
                    predicatesList.add(Collections.singletonList(triple.getPredicate()));
                }
                subQuery.add(triple, list);
            }
            if (subQuery.isEmpty())
                return; //nothing to do
            subQuery.addParentModifiers();
            assert subQuery.size() <= query.size();
            query = subQuery.build();
            if (query.isEmpty())
                return; // builder rejected the exclusive group during build
            SemanticCQueryMatch.Builder matchBuilder = matchBuilder().addExclusiveGroup(query);
            if (!hasAlternatives)
                return; // done
            for (List<Term> ps : Lists.cartesianProduct(predicatesList)) {
                EGQueryBuilder b = createEGQueryBuilder(query, subQuery);
                assert ps.size() == query.size();
                Iterator<Term> it = ps.iterator();
                for (Triple triple : query)
                    b.addAlternative(query, triple, triple.withPredicate(it.next()));
                b.addParentModifiers();
                matchBuilder.addAlternative(query, b.build());
            }
        }

    }
}
