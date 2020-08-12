package br.ufsc.lapesd.riefederator.algebra.leaf;

import br.ufsc.lapesd.riefederator.TestContext;
import br.ufsc.lapesd.riefederator.description.molecules.Atom;
import br.ufsc.lapesd.riefederator.description.molecules.Molecule;
import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.model.term.std.StdLit;
import br.ufsc.lapesd.riefederator.model.term.std.StdURI;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.endpoint.Capability;
import br.ufsc.lapesd.riefederator.query.endpoint.impl.EmptyEndpoint;
import br.ufsc.lapesd.riefederator.query.modifiers.*;
import br.ufsc.lapesd.riefederator.query.results.impl.MapSolution;
import br.ufsc.lapesd.riefederator.webapis.description.AtomAnnotation;
import br.ufsc.lapesd.riefederator.webapis.description.AtomInputAnnotation;
import com.google.common.collect.Sets;
import org.apache.jena.vocabulary.XSD;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.function.BiFunction;

import static br.ufsc.lapesd.riefederator.query.parse.CQueryContext.annotateTerm;
import static br.ufsc.lapesd.riefederator.query.parse.CQueryContext.createQuery;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.testng.Assert.*;

@Test(groups = {"fast"})
public class QueryOpTest implements TestContext {
    public static final @Nonnull StdLit i23 = StdLit.fromUnescaped("23", new StdURI(XSD.xint.getURI()));
    private static final EmptyEndpoint empty = new EmptyEndpoint();

    private static final @Nonnull Atom atom1 = Molecule.builder("Atom1").buildAtom();
    private static final @Nonnull Atom atom2 = Molecule.builder("Atom2").buildAtom();
    private static final @Nonnull Atom atom3 = Molecule.builder("Atom3").buildAtom();

    @DataProvider
    public static @Nonnull Object[][] factoryData() {
        BiFunction<CQuery, Set<String>, FreeQueryOp> f1 =
                (q, p) -> {
                    FreeQueryOp op = new FreeQueryOp(q);
                    if (p != null)
                        op.modifiers().add(Projection.advised(p));
                    return op;
                };
        BiFunction<CQuery, Set<String>, FreeQueryOp> f2 =
                (q, p) -> {
                    QueryOp op = new QueryOp(empty, q);
                    if (p != null)
                        op.modifiers().add(Projection.advised(p));
                    return op;
                };
        return new Object[][] { {f1}, {f2} };
    }

    @Test(dataProvider = "factoryData")
    public void testNoVars(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = CQuery.from(new Triple(Alice, knows, Bob));
        FreeQueryOp node = fac.apply(query, null);
        if (node instanceof QueryOp)
            assertSame(((QueryOp)node).getEndpoint(), empty);
        assertEquals(node.getQuery(), query);
        assertEquals(node.getResultVars(), emptySet());
        assertFalse(node.isProjected());
    }

    @Test(dataProvider = "factoryData")
    public void testVarsInTriple(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        FreeQueryOp node = fac.apply(CQuery.from(new Triple(Alice, knows, x)), null);
        assertEquals(node.getResultVars(), singleton("x"));
        assertEquals(node.getRequiredInputVars(), emptySet());
        assertEquals(node.getOptionalInputVars(), emptySet());
        assertEquals(node.getInputVars(), emptySet());
        assertEquals(node.getPublicVars(), singleton("x"));
        assertEquals(node.getStrictResultVars(), singleton("x"));
        assertFalse(node.isProjected());
    }

    @Test(dataProvider = "factoryData")
    public void testVarInFilter(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                x, age, y, SPARQLFilter.build("?y < ?z")
        );
        FreeQueryOp node = fac.apply(query, null);
        assertFalse(node.isProjected());
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y"));
        assertEquals(node.getAllVars(), Sets.newHashSet("x", "y", "z"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y")); //z is not input
        assertEquals(node.getInputVars(), emptySet());
        assertEquals(node.getRequiredInputVars(), emptySet());
        assertEquals(node.getOptionalInputVars(), emptySet());
    }


    @Test(dataProvider = "factoryData")
    public void testRequiredInputInFilter(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                x, age, y, SPARQLFilter.build("?y < ?z"),
                annotateTerm(z, AtomInputAnnotation.asRequired(atom1, "a1").get())
        );
        FreeQueryOp node = fac.apply(query, null);
        assertFalse(node.isProjected());
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y"));
        assertEquals(node.getStrictResultVars(), Sets.newHashSet("x", "y"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "z"));
        assertEquals(node.getRequiredInputVars(), singleton("z"));
        assertEquals(node.getOptionalInputVars(), emptySet());
        assertEquals(node.getInputVars(), singleton("z"));
    }

    @Test(dataProvider = "factoryData")
    public void testOptionalInputInFilter(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                x, age, y, SPARQLFilter.build("?y < ?z"),
                annotateTerm(z, AtomInputAnnotation.asOptional(atom1, "a1").get())
        );
        FreeQueryOp node = fac.apply(query, null);
        assertFalse(node.isProjected());
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y"));
        assertEquals(node.getStrictResultVars(), Sets.newHashSet("x", "y"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "z"));
        assertEquals(node.getRequiredInputVars(), emptySet());
        assertEquals(node.getOptionalInputVars(), singleton("z"));
        assertEquals(node.getInputVars(), singleton("z"));
    }

    @Test(dataProvider = "factoryData")
    public void testStrictResultVars(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                x, age, y, AtomInputAnnotation.asRequired(atom1, "a2").get(),
                SPARQLFilter.build("?y < ?z"),
                annotateTerm(z, AtomInputAnnotation.asOptional(atom2, "a2").get())
        );
        FreeQueryOp node = fac.apply(query, null);
        assertFalse(node.isProjected());
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y"));
        assertEquals(node.getStrictResultVars(), singleton("x"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "z"));
        assertEquals(node.getRequiredInputVars(), singleton("y"));
        assertEquals(node.getOptionalInputVars(), singleton("z"));
        assertEquals(node.getInputVars(), Sets.newHashSet("y", "z"));
    }


    @Test(dataProvider = "factoryData")
    public void testProject(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                x, age, y, AtomInputAnnotation.asRequired(atom1, "a2").get(),
                SPARQLFilter.build("?y < ?z"),
                annotateTerm(z, AtomInputAnnotation.asOptional(atom2, "a2").get())
        );
        FreeQueryOp node = fac.apply(query, singleton("x"));
        assertTrue(node.isProjected());
        assertEquals(node.getResultVars(), Sets.newHashSet("x"));
        assertEquals(node.getStrictResultVars(), singleton("x"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "z"));
        assertEquals(node.getRequiredInputVars(), singleton("y"));
        assertEquals(node.getOptionalInputVars(), singleton("z"));
        assertEquals(node.getInputVars(), Sets.newHashSet("y", "z"));
    }

    @Test(dataProvider = "factoryData")
    public void testProjectFilterInput(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                x, age, y,
                SPARQLFilter.build("?y < ?z"),
                annotateTerm(z, AtomInputAnnotation.asOptional(atom2, "a2").get())
        );
        FreeQueryOp node = fac.apply(query, singleton("x"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "z"));
        assertEquals(node.getResultVars(), singleton("x"));
        assertEquals(node.getInputVars(), singleton("z"));
        assertEquals(node.getRequiredInputVars(), emptySet());
        assertEquals(node.getOptionalInputVars(), singleton("z"));
    }

    @Test(dataProvider = "factoryData")
    public void testVarsInConjunctive(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = CQuery.from(asList(new Triple(Alice, knows, x),
                                          new Triple(x, knows, y)));
        FreeQueryOp node = fac.apply(query, null);
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y"));
        assertFalse(node.isProjected());
        assertFalse(node.toString().startsWith("π"));
    }


    @Test(dataProvider = "factoryData")
    public void testVarsInProjection(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = CQuery.from(asList(new Triple(Alice, knows, x),
                                          new Triple(x, knows, y)));
        FreeQueryOp node = fac.apply(query, singleton("x"));
        assertEquals(node.getResultVars(), singleton("x"));
        assertTrue(node.isProjected());
        assertTrue(node.toString().startsWith("π[x]("));
    }

    @Test(dataProvider = "factoryData")
    public void testVarsInOverriddenInputAnnotation(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                x, knows, y,
                x, age, u, AtomInputAnnotation.asRequired(atom1, "a1").get(),
                x, name, x1,
                AtomInputAnnotation.asRequired(atom2, "a2").override(lit("Alice")).get(),
                SPARQLFilter.build("regex(str(?x1), \"Alice.*\")"),
                y, name, y1,
                AtomInputAnnotation.asRequired(atom3, "a3").override(lit("Bob")).get(),
                SPARQLFilter.build("regex(str(?y1), \"Bob.*\")")
        );
        FreeQueryOp node = fac.apply(query, null);
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y", "u", "x1", "y1"));
        assertEquals(node.getOptionalInputVars(), emptySet());
        assertEquals(node.getRequiredInputVars(), singleton("u"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "u", "x1", "y1"));
        assertEquals(node.getStrictResultVars(), Sets.newHashSet("x", "y", "x1", "y1"));
    }

    @Test(dataProvider = "factoryData")
    public void testCreateBoundNoOp(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(Alice, knows, x, x, knows, Bob, Distinct.REQUIRED);
        FreeQueryOp node = fac.apply(query, null);
        FreeQueryOp bound = node.createBound(MapSolution.build("y", Charlie));
        if (node instanceof QueryOp) {
            assertTrue(bound instanceof QueryOp);
            assertSame(((QueryOp) bound).getEndpoint(), ((QueryOp) node).getEndpoint());
        }

        assertEquals(bound.getQuery(), query);
        Modifier modifier = ModifierUtils.getFirst(Capability.DISTINCT,
                                                   bound.getQuery().getModifiers());
        assertNotNull(modifier);
        assertTrue(modifier.isRequired());
    }

    @Test(dataProvider = "factoryData")
    public void testCreateBoundBindingOneVar(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(x, knows, Alice, y, knows, x, Ask.ADVISED);
        FreeQueryOp node = fac.apply(query, null);
        FreeQueryOp bound = node.createBound(MapSolution.build("x", Bob));

        assertEquals(bound.getQuery(),
                     createQuery(Bob, knows, Alice, y, knows, Bob, Ask.ADVISED));
        Modifier modifier = ModifierUtils.getFirst(Capability.ASK, bound.getQuery().getModifiers());
        assertTrue(modifier instanceof Ask);
        assertFalse(modifier.isRequired());
    }

    @Test(dataProvider = "factoryData")
    public void testCreateBoundBindingOnlyVar(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = CQuery.from(asList(new Triple(x, knows, Alice),
                                          new Triple(x, age, i23)));
        FreeQueryOp node = fac.apply(query, null);
        FreeQueryOp bound = node.createBound(MapSolution.build("x", Bob));

        CQuery expected = CQuery.from(asList(new Triple(Bob, knows, Alice),
                                             new Triple(Bob, age,   i23)));
        assertEquals(bound.getQuery(), expected);
        assertTrue(bound.getQuery().getModifiers().isEmpty());
    }

    @Test(dataProvider = "factoryData")
    public void testBindingRemovesProjectedVar(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = CQuery.from(new Triple(x, knows, y));
        FreeQueryOp node = fac.apply(query, singleton("x"));
        FreeQueryOp bound = node.createBound(MapSolution.build(x, Alice));

        CQuery actual = bound.getQuery();
        assertEquals(actual, CQuery.from(new Triple(Alice, knows, y)));

        assertEquals(bound.getResultVars(), emptySet());
        assertTrue(bound.isProjected());
    }

    @Test(dataProvider = "factoryData")
    public void testBindingRemovesResultVar(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = CQuery.from(new Triple(x, knows, y));
        FreeQueryOp node = fac.apply(query, null);
        FreeQueryOp bound = node.createBound(MapSolution.build(y, Bob));

        assertEquals(bound.getQuery(), CQuery.from(new Triple(x, knows, Bob)));
        assertEquals(bound.getResultVars(), singleton("x"));
        assertFalse(bound.hasInputs());
        assertFalse(bound.isProjected());
    }

    @Test(dataProvider = "factoryData")
    public void testBindingNoChangePreservesAnnotations(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(Alice, AtomAnnotation.of(atom1),
                knows, y, AtomInputAnnotation.asRequired(atom2, "atom2").get());
        FreeQueryOp node = fac.apply(query, null);
        FreeQueryOp bound = node.createBound(MapSolution.build(x, Bob));

        assertEquals(bound.getResultVars(), singleton("y"));
        assertTrue(bound.hasInputs());
        assertEquals(bound.getRequiredInputVars(), singleton("y"));
        assertTrue(bound.getQuery().hasTermAnnotations());
        assertFalse(bound.getQuery().hasTripleAnnotations());
        assertEquals(bound.getQuery(), query);
    }

    @Test(dataProvider = "factoryData")
    public void testBindingPreservesAnnotations(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                x, AtomInputAnnotation.asRequired(atom1, "atom1").get(),
                        knows, y, AtomInputAnnotation.asRequired(atom2, "atom2").get(),
                Alice, AtomAnnotation.of(atom3), knows, x);
        FreeQueryOp node = fac.apply(query, null);
        FreeQueryOp bound = node.createBound(MapSolution.build(x, Bob));

        assertEquals(bound.getResultVars(), singleton("y"));
        assertTrue(bound.hasInputs());
        assertEquals(bound.getRequiredInputVars(), singleton("y"));

        CQuery expected = createQuery(
                Bob, AtomInputAnnotation.asRequired(atom1, "atom1").get(),
                        knows, y, AtomInputAnnotation.asRequired(atom2, "atom2").get(),
                Alice, AtomAnnotation.of(atom3), knows, Bob);
        //noinspection SimplifiedTestNGAssertion
        assertTrue(bound.getQuery().equals(expected));
        assertTrue(bound.getQuery().getTermAnnotations(Bob)
                                   .contains(AtomInputAnnotation.asRequired(atom1, "atom1").get()));
    }

    @Test(dataProvider = "factoryData")
    public void testBindInputInFilterOfQuery(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                x, age, y,
                SPARQLFilter.build("?y < ?u"),
                annotateTerm(u, AtomInputAnnotation.asRequired(atom1, "a1").get())
        );
        FreeQueryOp node = fac.apply(query, null);
        assertEquals(node.getInputVars(), singleton("u"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "u"));
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y"));

        FreeQueryOp bound = node.createBound(MapSolution.build(u, lit(23)));

        assertEquals(bound.getPublicVars(), Sets.newHashSet("x", "y"));
        assertEquals(bound.getInputVars(), emptySet());
        assertEquals(bound.getQuery().getModifiers().size(), 1);
        Modifier modifier = bound.getQuery().getModifiers().iterator().next();
        assertTrue(modifier instanceof SPARQLFilter);
        assertEquals(((SPARQLFilter)modifier).getVars(), singleton("y"));
    }

    @Test(dataProvider = "factoryData")
    public void testBindInputInFilter(BiFunction<CQuery, Set<String>, FreeQueryOp> fac) {
        CQuery query = createQuery(
                Alice, knows, x,
                Alice, age, y, SPARQLFilter.build("?y < ?u"),
                x, age, z, SPARQLFilter.build("?z < ?y"),
                annotateTerm(u, AtomInputAnnotation.asRequired(atom1, "a1").get())
        );
        FreeQueryOp node = fac.apply(query, null);
        assertEquals(node.getInputVars(), singleton("u"));
        assertEquals(node.getPublicVars(), Sets.newHashSet("x", "y", "y", "z", "u"));
        assertEquals(node.getResultVars(), Sets.newHashSet("x", "y", "y", "z"));

        assertEquals(node.modifiers(), query.getModifiers());

        FreeQueryOp bound = node.createBound(MapSolution.build(u, lit(23)));
        assertEquals(bound.getInputVars(), emptySet());
        assertEquals(bound.getPublicVars(), Sets.newHashSet("x", "y", "y", "z"));

        assertEquals(bound.getQuery().getModifiers().size(), 2);
        assertTrue(bound.getQuery().getModifiers().contains(SPARQLFilter.build("?z < ?y")));
        assertTrue(bound.getQuery().getModifiers().contains(SPARQLFilter.build("?y < 23")));

        assertEquals(bound.modifiers().filters().size(), 2);
        assertTrue(bound.modifiers().filters().contains(SPARQLFilter.build("?z < ?y")));
        assertTrue(bound.modifiers().filters().contains(SPARQLFilter.build("?y < 23")));
    }
}