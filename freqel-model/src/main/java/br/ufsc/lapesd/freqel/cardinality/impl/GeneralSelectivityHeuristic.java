package br.ufsc.lapesd.freqel.cardinality.impl;

import br.ufsc.lapesd.freqel.V;
import br.ufsc.lapesd.freqel.algebra.Cardinality;
import br.ufsc.lapesd.freqel.cardinality.CardinalityHeuristic;
import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.model.term.Term;
import br.ufsc.lapesd.freqel.model.term.URI;
import br.ufsc.lapesd.freqel.model.term.std.StdURI;
import br.ufsc.lapesd.freqel.query.CQuery;
import br.ufsc.lapesd.freqel.query.MutableCQuery;
import br.ufsc.lapesd.freqel.query.endpoint.TPEndpoint;
import br.ufsc.lapesd.freqel.util.Bitset;
import br.ufsc.lapesd.freqel.util.indexed.FullIndexSet;
import br.ufsc.lapesd.freqel.util.indexed.IndexSet;
import br.ufsc.lapesd.freqel.util.indexed.subset.IndexSubset;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static br.ufsc.lapesd.freqel.model.Triple.Position.OBJ;
import static br.ufsc.lapesd.freqel.model.Triple.Position.SUBJ;
import static java.util.stream.Collectors.joining;

@SuppressWarnings("StatementWithEmptyBody")
public class GeneralSelectivityHeuristic implements CardinalityHeuristic {
    public static final @Nonnull GeneralSelectivityHeuristic DEFAULT
            = new GeneralSelectivityHeuristic();
    private static final URI BOUND_TERM = new StdURI("urn:freqelbound:PropertySelectivityCardinalityHeuristic");

    private static final int triplePenalty = 10000000;
    private static final int fallbackDoublePenalty = 50000;
    private static final int fallbackPredicatePenalty = 100;
    private static final int fallbackSubjectPenalty = 5000;
    private static final int fallbackObjectPenalty = 200;
    private static final Map<Term, Integer> doublePenalty;
    private static final Map<Term, Integer> subjectPenalty;
    private static final Map<Term, Integer> objectPenalty;


    static {
        Map<URI, Integer> map = new HashMap<>();
        map.put(V.RDF.type,            1000000);
        map.put(V.DCT.title,            500000);
        map.put(V.RDFS.label,           500000);
        map.put(V.DCT.description,      250000);
        map.put(V.OWL.sameAs,           250000);
        map.put(V.RDFS.seeAlso,         125000);
        map.put(V.FOAF.name,             60000);
        map.put(V.FOAF.familyName,       45000);
        map.put(V.FOAF.givenName,        45000);
        map.put(V.FOAF.mbox,             45000);
        map.put(V.FOAF.mbox_sha1sum,     45000);
        doublePenalty = ImmutableMap.copyOf(map);

        map.clear();
        map.put(V.RDF.type,             50000);
        map.put(V.RDFS.label,            5000);
        map.put(V.FOAF.givenName,        5000);
        map.put(V.FOAF.name,             2500);
        map.put(V.DCT.title,             1000);
        map.put(V.DCT.description,       1000);
        map.put(V.FOAF.familyName,       1000);
        map.put(V.RDFS.seeAlso,           300);
        map.put(V.OWL.sameAs,             150);
        map.put(V.FOAF.mbox,              100);
        map.put(V.FOAF.mbox_sha1sum,      100);
        subjectPenalty = ImmutableMap.copyOf(map);

        map.clear();
        map.put(V.OWL.sameAs,            70);
        map.put(V.DCT.title,             50);
        map.put(V.RDFS.label,            50);
        map.put(V.DCT.description,       50);
        map.put(V.RDF.type,              20);
        map.put(V.RDFS.seeAlso,          10);
        map.put(V.FOAF.name,             10);
        map.put(V.FOAF.familyName,       10);
        map.put(V.FOAF.givenName,        10);
        map.put(V.FOAF.mbox,             10);
        map.put(V.FOAF.mbox_sha1sum,     10);
        objectPenalty = ImmutableMap.copyOf(map);
    }

    @Inject public GeneralSelectivityHeuristic() { }

    @Override
    public @Nonnull Cardinality estimate(@Nonnull CQuery query, @Nullable TPEndpoint ignored) {
        int value;
        if (query.size() == 1)
            value = globalEstimateTriple(query.get(0));
        else
            value = new Estimator(query).visitAllComponents();
        return value < 0 ? Cardinality.UNSUPPORTED : Cardinality.guess(value);
    }

    @VisibleForTesting
    class Estimator {
        @Nonnull CQuery query;
        @Nonnull IndexSubset<Triple> visited, pathVisited;
        @Nonnull ArrayDeque<Triple> stack, pathStack;
        @Nonnull PathEstimator pathEstimator;
        int product = -1;

        public Estimator(@Nonnull CQuery query) {
            IndexSet<Triple> set = query.attr().getSet();
            if (!(set instanceof FullIndexSet)) {
                MutableCQuery copy = new MutableCQuery();
                copy.addAll(query);
                query = copy;
            }
            this.query = query;
            visited = query.attr().getSet().emptySubset();
            pathVisited = query.attr().getSet().emptySubset();
            stack  = new ArrayDeque<>();
            pathStack  = new ArrayDeque<>();
            pathEstimator = new PathEstimator(query);
        }

        private boolean visitComponent() {
            int best = -1;
            int clearIdx = visited.getBitset().nextClearBit(0);
            if (clearIdx >= visited.getParent().size())
                return false; //no more components
            stack.push(visited.getParent().get(clearIdx));
            while (!stack.isEmpty()) {
                Triple triple = stack.pop();
                if (visited.add(triple)) {
                    best = avg(best, pathEstimator.visitPath(triple, visited));
                    query.attr().triplesWithTerm(triple.getSubject()).forEach(stack::push);
                    query.attr().triplesWithTerm(triple.getObject()).forEach(stack::push);
                    // only consider connection through predicate if it leads toa  subject or object
                    // ex:Alice ex:pred ?z and ex:Bob ex:pred ?y are disjoint (and dangerous)
                    Term p = triple.getPredicate();
                    query.attr().triplesWithTerm(p).forEach(next -> {
                        if (next.getSubject().equals(p) || next.getObject().equals(p))
                            stack.push(next);
                    });
                }
            }
            // disconnectedness here is really bad, since we allow connectedness through variables
            // thus any disconnected components likely suffer from really bad cartesian products
            // However since estimates are already sometimes large, computing actual products
            // could lead to overflow quickly. Hence, multiply() is a constant multiplaction
            // with the maximal argument
            if (product < 0) product = best;
            else             product = multiply(product, best);
            return true; //visited a component
        }

        public int visitAllComponents() {
            while (visitComponent());
            return product;
        }
    }

    @VisibleForTesting
    class PathEstimator {
        @Nonnull CQuery query;
        @Nonnull IndexSubset<Triple> visited;
        @Nonnull ArrayDeque<Triple> stack = new ArrayDeque<>();
        @Nullable Term core = null;
        @Nonnull Side fromSubj, fromObj;
        @Nonnull IndexSubset<Term> coreCandidates;
        @Nonnull int[] tripleCost;

        private class Side {
            @Nonnull final Triple.Position position, opposite;
            @Nonnull final ArrayDeque<Triple> queue = new ArrayDeque<>();
            @Nonnull final IndexSubset<Triple> localTriples, boundary;
            @Nonnull final IndexSubset<Term> localTerms;

            public Side(@Nonnull Triple.Position position) {
                assert position != Triple.Position.PRED;
                this.position = position;
                this.opposite = position.opposite();
                this.localTriples = visited.getParent().emptySubset();
                this.boundary = visited.getParent().emptySubset();
                this.localTerms = query.attr().tripleTerms().emptySubset();
            }

            public void clear() {
                queue.clear();
                localTriples.clear();
                boundary.clear();
                localTerms.clear();
            }

            private boolean visit(@Nonnull Triple t, int tripleIdx) {
                IndexSet<Triple> allTriples = localTriples.getParent();
                boolean added = localTriples.setIndex(tripleIdx, allTriples);
                assert added : "Triple is new on shared, but is not new on the local version!";

                // set cost. Due to shared visited, will only be set once
                if (boundary.hasIndex(tripleIdx, allTriples))
                    tripleCost[tripleIdx] = globalEstimateTriple(t);
                else
                    tripleCost[tripleIdx] = min(tripleCost[tripleIdx], estimateInnerTriple(t));

                // explore subject <--> object joins
                Term term = t.get(position);
                if (localTerms.add(term)) {
                    for (Triple next : query.attr().triplesWithTerm(term)) {
                        if (next.get(opposite).equals(term))
                            queue.add(next);
                    }
                    if (core == null && !coreCandidates.add(term))
                        core = term; //found the core
                }
                return true; // visited
            }

            public boolean step() {
                IndexSet<Triple> allTriples = visited.getParent();
                while (!queue.isEmpty()) {
                    Triple triple = queue.remove();
                    int tripleIdx = allTriples.indexOf(triple);
                    if (!visited.setIndex(tripleIdx, allTriples))
                        continue; //already visited, try another triple
                    if (visit(triple, tripleIdx))
                        return true;  //advanced one triple
                }
                return false; // no work done and no work left
            }

            public void crossBorder() {
                assert core != null;
                visited.clear();
                assert localTriples.containsAll(boundary);
                visited.addAll(localTriples);
                localTerms.clear();
                for (Triple localTriple : localTriples) {
                    Term term = localTriple.get(position);
                    if (!localTerms.add(term)) continue;
                    queue.addAll(query.attr().triplesWithTermAt(term, opposite));
                }

                assert localTerms.contains(core);
                while (step()) ;
            }

            private void addBoundary(@Nonnull Triple triple) {
                assert query.attr().triplesWithTermAt(triple.get(opposite), position).isEmpty()
                        : "add() on non-boundary triple " + triple + " position="+position;
                IndexSet<Triple> allTriples = boundary.getParent();
                int idx = allTriples.indexOf(triple);
                boolean added = boundary.setIndex(idx, allTriples);
                assert added : "Boundary triple had been previously added";
                boolean visited = visit(triple, idx);
                assert visited : "Boundary triple was already visited";
            }

            private int estimateInnerTriple(@Nonnull Triple triple) {
                assert !query.attr().triplesWithTermAt(triple.get(opposite), position).isEmpty()
                        : "Tried to estimate boundary triple " + triple + " as if non-boundary";
                // when fromSubj crosses the core and processes a triple from the other side,
                // always allow it to replace the subject. This has the effect of sometimes
                // reducing triple cardinality to 1, which is not entirely accurate, but
                // is better than overstimation which will probably be happening. The Side
                // objects do not travel more than one triple beyond core, so larger paths
                // will retain some penalty from ?x ex:p ex:o triples in the fromObj side.
                //
                // when fromObj crosses the border, the transformation will only be allowed if
                // it would promote a ?x ex:p ?y pattern to a ?x ex:p ex:o pattern.
                // Allowing a transformation that reduces cardinality to 1 would be way too
                // imprecise.
                if (opposite == SUBJ || triple.get(position).isVar())
                    triple = triple.with(opposite, BOUND_TERM);
                int value = globalEstimateTriple(triple);
                assert value >= 0;
                return value;
            }

            public int getEstimate() {
                IndexSet<Triple> allTriples = localTriples.getParent();
                assert localTriples.containsAll(boundary) : "Some boundary triples were not visited";
                assert core != null : "No core term found, would overshoot estimates";

                Bitset bs = localTriples.getBitset();
                int sum = -2;
                for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
                    if (allTriples.get(i).get(opposite).equals(core))
                        continue; //do not count trespassing triples
                    if (sum == -2) sum = tripleCost[i];
                    else           sum = sum(sum, tripleCost[i]);
                }
                return sum;
            }
        }

        public PathEstimator(@Nonnull CQuery query) {
            this.query = query;
            //noinspection AssertWithSideEffects
            assert query.attr().getSet() instanceof FullIndexSet;
            this.visited = query.attr().getSet().emptySubset();
            this.coreCandidates = query.attr().tripleTerms().emptySubset();
            this.fromSubj = new Side(OBJ);
            this.fromObj = new Side(SUBJ);
            tripleCost = new int[query.size()];
            Arrays.fill(tripleCost, -1);
        }

        private void clear() {
            stack.clear();
            visited.clear();
            fromSubj.clear();
            fromObj.clear();
            core = null;
            coreCandidates.clear();
        }

        public int visitPath(@Nonnull Triple triple, @Nonnull IndexSubset<Triple> outerVisited) {
            assert stack.isEmpty();
            clear();
            Arrays.fill(tripleCost, -1);
            findComponent(triple); //find the subj-obj connected component of triple
            outerVisited.addAll(visited);
            if (visited.size() == 1) {
                applyStarSelectivity();
                int idx = visited.getBitset().nextSetBit(0);
                assert idx >= 0;
                assert tripleCost[idx] >= 0;
                return tripleCost[idx];
            }

            int oldVisitedSize = visited.size();
            visited.clear();
            //find the core and estimate best triple cardinalities
            findCore();             assert visited.size() == oldVisitedSize;
            applyStarSelectivity(); assert visited.size() == oldVisitedSize;
            applyDoubleEndBonus();  assert visited.size() == oldVisitedSize;
//            dumpCosts();
            return sumCosts(visited);
        }

        /**
         * Prints to System.out all triples and their estimated cardinalities..
         *
         * For debug purposes only.
         */
        private void dumpCosts() {
            int[] indices = new int[visited.size()];
            int[] sizes = new int[visited.size()];
            int idx = 0;
            Bitset bs = visited.getBitset();
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
                Triple triple = visited.getParent().get(i);
                String s = triple.toString();
                System.out.print(s + "  ");
                indices[idx] = i;
                sizes[idx] = s.length() + 2;
                ++idx;
            }
            System.out.println();
            for (int i = 0; i < visited.size(); i++) {
                String cost = String.format("%d", tripleCost[indices[i]]);
                System.out.print(cost);
                for (int j = 0; j < sizes[i] - cost.length(); j++)
                    System.out.print(' ');
            }
            System.out.println();
            System.out.printf("Subject boundary: %s\n",
                    fromSubj.boundary.stream().map(Triple::toString).collect(joining(" ")));
            System.out.printf("Object boundary: %s\n",
                    fromSubj.boundary.stream().map(Triple::toString).collect(joining(" ")));
            System.out.printf("Core: %s. Total path estimate: %d\n", core, sumCosts(visited));
        }

        private int sumCosts(@Nonnull IndexSubset<Triple> subset) {
            Bitset bs = subset.getBitset();
            int sum = -2;
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
                if (sum == -2) sum = tripleCost[i];
                else           sum = sum(sum, tripleCost[i]);
            }
            return sum;
        }

        private void applyStarSelectivity() {
            IndexSubset<Term> visitedSubjects = query.attr().tripleTerms().emptySubset();
            IndexSet<Triple> allTriples = visited.getParent();
            for (Triple triple : visited) {
                Term s = triple.getSubject();
                if (!visitedSubjects.add(s))
                    continue;
                // get the star of this subject
                IndexSubset<Triple> star = query.attr().triplesWithTermAt(s, SUBJ);
                if (star.size() == 1)
                    continue; // no discount will ever be given

                //compute a weight bonus factor. This will vary from 0 (all objects are bound)
                // to 1 (all objects are free -- no bonus)
                int freeObj = 0;
                for (Triple starTriple : star) {
                    if (starTriple.getObject().isVar()) ++freeObj;
                }
                double factor = freeObj / (double) star.size();

                Bitset bs = star.getBitset();
                for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
                    if (visited.hasIndex(i, allTriples)
                            && star.getAtIndex(i, allTriples).getObject().isVar()) {
                        tripleCost[i] = (int) Math.ceil(tripleCost[i] * factor);
                    } // else: do not apply bonus since object-bound is already "cheap"
                }
            }
        }

        private void applyDoubleEndBonus() {
            int boundSubj = 0, boundObj = 0;
            for (Triple triple : fromSubj.boundary) {
                if (triple.getSubject().isGround()) ++boundSubj;
            }
            for (Triple triple : fromObj.boundary) {
                if (triple.getObject().isGround()) ++boundObj;
            }
            // best case: all bound, then factor = 0.5
            // medium case example: all bound on subj, half on obj, then factor = 0.66
            // worst case: none bound, then abort bonus
            double boundSubjRate = boundSubj / (double) fromSubj.boundary.size();
            double boundObjRate  =  boundObj / (double)  fromObj.boundary.size();
            if (boundSubjRate+boundObjRate == 0)
                return; //no bonus to apply
            double factor = 1/(boundSubjRate + boundObjRate);

            // do not apply bonus to the boundary triples
            IndexSubset<Triple> boundaries = visited.getParent().emptySubset();
            boundaries.addAll(fromSubj.boundary);
            boundaries.addAll(fromObj.boundary);
            Bitset bs = visited.getBitset();
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
                tripleCost[i] = (int)Math.ceil(factor * tripleCost[i]);
                assert tripleCost[i] >= 1 : "Zeroed-out a cost! this is a bug";
            }
        }

        private void findComponent(@Nonnull Triple triple) {
            stack.push(triple);
            while (!stack.isEmpty()) {
                Triple t = stack.pop();
                if (!visited.add(t))
                    continue;

                // try subj --> obj joins
                IndexSubset<Triple> set = query.attr().triplesWithTermAt(t.getSubject(), OBJ);
                if (set.isEmpty()) fromSubj.addBoundary(t);
                else               set.forEach(stack::push);
                // try obj --> subj joins
                set = query.attr().triplesWithTermAt(t.getObject(), SUBJ);
                if (set.isEmpty()) fromObj.addBoundary(t);
                else               set.forEach(stack::push);
            }
        }

        private void findCore() {
            //boundaries are already visited
            visited.addAll(fromSubj.boundary);
            visited.addAll(fromObj.boundary);
            //explore from both sides in parallel
            boolean fromSubjActive = true, fromObjActive = true;
            while (fromSubjActive || fromObjActive) {
                fromSubjActive = fromSubjActive && fromSubj.step();
                fromObjActive = fromObjActive && fromObj.step();
            }
            assert core != null;
            // often the path has a best direction for bind joins. Get the cheapest side
            // around core and reevaluate the other side in the reverse direction of the one it
            // was previously evaluated.
            if (fromSubj.getEstimate() < fromObj.getEstimate()) fromSubj.crossBorder();
            else                                                fromObj.crossBorder();
        }
    }

    private static int min(int left, int right) {
        if ( left < 0) return right;
        if (right < 0) return left;
        return Math.min(left, right);
    }

    private static int avg(int left, int right) {
        if ( left < 0) return right;
        if (right < 0) return left;
        return (int)Math.ceil((left+right)/2.0);
    }

    private static int sum(int left, int right) {
        if (left < 0 && right < 0) return -1;
        if (left  < 0) return right*2;
        if (right < 0) return left*2;
        return left+right;
    }

    private static int multiply(int left, int right) {
        int m = max(left, right);
        return m < 0 ? -1 : Math.max(m*4, fallbackSubjectPenalty);
    }

    private static int max(int left, int right) {
        if ( left < 0) return right;
        if (right < 0) return left;
        return Math.max(left, right);
    }

    public int globalEstimateTriple(@Nonnull Triple t) {
        Term s = t.getSubject(), p = t.getPredicate(), o = t.getObject();
        boolean sv = s.isVar(), pv = p.isVar(), ov = o.isVar();
        if (sv && pv && ov) {
            return triplePenalty;
        } else if ((sv && ov) || (sv && pv)) {
            return doublePenalty.getOrDefault(p, fallbackDoublePenalty);
        } else if (pv) { // also pv && ov
            return fallbackPredicatePenalty;
        } else if (sv) {
            return subjectPenalty.getOrDefault(p, fallbackSubjectPenalty);
        } else if (ov) {
            return objectPenalty.getOrDefault(p, fallbackObjectPenalty);
        } else  { // !sv && !pv && !ov
            return 1;
        }
    }
}
