package br.ufsc.lapesd.riefederator.algebra;

import br.ufsc.lapesd.riefederator.TestContext;
import br.ufsc.lapesd.riefederator.algebra.inner.CartesianOp;
import br.ufsc.lapesd.riefederator.algebra.inner.ConjunctionOp;
import br.ufsc.lapesd.riefederator.algebra.inner.JoinOp;
import br.ufsc.lapesd.riefederator.algebra.inner.UnionOp;
import br.ufsc.lapesd.riefederator.algebra.leaf.FreeQueryOp;
import br.ufsc.lapesd.riefederator.algebra.leaf.QueryOp;
import br.ufsc.lapesd.riefederator.algebra.util.TreeUtils;
import br.ufsc.lapesd.riefederator.description.molecules.Atom;
import br.ufsc.lapesd.riefederator.description.molecules.Molecule;
import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.query.MutableCQuery;
import br.ufsc.lapesd.riefederator.query.endpoint.impl.EmptyEndpoint;
import br.ufsc.lapesd.riefederator.query.modifiers.SPARQLFilter;
import br.ufsc.lapesd.riefederator.util.CollectionUtils;
import br.ufsc.lapesd.riefederator.webapis.description.AtomInputAnnotation;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static br.ufsc.lapesd.riefederator.algebra.util.TreeUtils.isAcyclic;
import static br.ufsc.lapesd.riefederator.algebra.util.TreeUtils.streamPreOrder;
import static br.ufsc.lapesd.riefederator.query.parse.CQueryContext.createQuery;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.*;

@Test(groups = {"fast"})
public class TreeUtilsTest implements TestContext {
    private static final EmptyEndpoint ep = new EmptyEndpoint();
    private final Atom person = Molecule.builder("Person").buildAtom();

    @DataProvider
    public static Object[][] iterateDeepLeft() {
        QueryOp q1 = new QueryOp(ep, createQuery(x, knows, Alice));
        QueryOp q2 = new QueryOp(ep, createQuery(x, knows, y));
        JoinOp j1 = JoinOp.create(q1, q2);
        QueryOp q3 = new QueryOp(ep, createQuery(y, knows, Charlie));
        QueryOp q4 = new QueryOp(ep, createQuery(y, knows, Dave));
        JoinOp j2 = JoinOp.create(q3, q4);
        JoinOp j3 = JoinOp.create(j1, j2);
        QueryOp q5 = new QueryOp(ep, createQuery(z, knows, Alice));
        CartesianOp c1 = new CartesianOp(asList(j3, q5));

        return new Object[][] {
                new Object[] {q1, singletonList(q1)},
                new Object[] {j1, asList(j1, q1, q2)},
                new Object[] {j3, asList(j3, j1, q1, q2, j2, q3, q4)},
                new Object[] {c1, asList(c1, j3, j1, q1, q2, j2, q3, q4, q5)},
        };
    }

    @Test(dataProvider = "iterateDeepLeft")
    public void testDeepLeft(@Nonnull Op root, @Nonnull List<Op> expected) {
        List<Op> iterated = new ArrayList<>();
        Iterator<Op> it = TreeUtils.iteratePreOrder(root);
        while (it.hasNext())
            iterated.add(it.next());
        assertEquals(iterated, expected);

        List<Op> streamed = streamPreOrder(root).collect(toList());
        assertEquals(streamed, expected);
    }

    @Test
    public void testIsTreeForgivesQueryNodes() {
        QueryOp q1  = new QueryOp(ep, createQuery(x, knows, Alice));
        QueryOp q2  = new QueryOp(ep, createQuery(x, knows, Bob));
        QueryOp q1a = new QueryOp(ep, createQuery(
                x, AtomInputAnnotation.asRequired(person, "person").get(),
                        knows, Alice));
        JoinOp j1 = JoinOp.create(q1,  q2);
        JoinOp j2 = JoinOp.create(q1a, q2);
        Op root = UnionOp.builder().add(j1).add(j2).build();

        assertFalse(TreeUtils.isTree(root, false));
        assertTrue(TreeUtils.isTree(root, true));
        assertFalse(TreeUtils.isTree(root));
        assertTrue(isAcyclic(root));
    }

    @Test
    public void testIsAcyclicSimple() {
        QueryOp n1 = new QueryOp(ep, createQuery(Alice, knows, x));
        QueryOp n2 = new QueryOp(ep, createQuery(x, knows, Bob));
        JoinOp root = JoinOp.create(n1, n2);

        assertTrue(isAcyclic(n1));
        assertTrue(isAcyclic(root));
    }

    @Test
    public void testIsAcyclicWithQueryNodeReuse() {
        QueryOp n1 = new QueryOp(ep, createQuery(Alice, knows, x));
        QueryOp n2 = new QueryOp(ep, createQuery(x, knows, Bob));
        JoinOp j1 = JoinOp.create(n1, n2);
        JoinOp j2 = JoinOp.create(n1, n2);
        Op r = UnionOp.builder().add(j1).add(j2).build();

        assertEquals(Stream.of(j1,j2,r).filter(n -> !isAcyclic(n)).collect(toList()), emptyList());
    }

    @Test
    public void testIsAcyclicWithJoinNodeReuse() {
        EmptyEndpoint ep2 = new EmptyEndpoint();
        QueryOp n1 = new QueryOp(ep, createQuery(Alice, knows, x));
        QueryOp n2 = new QueryOp(ep, createQuery(x, knows, y));
        QueryOp n3a = new QueryOp(ep , createQuery(y, knows, Bob));
        QueryOp n3b = new QueryOp(ep2, createQuery(y, knows, Bob));
        JoinOp j1 = JoinOp.create(n1, n2);
        JoinOp j2 = JoinOp.create(j1, n3a);
        JoinOp j3 = JoinOp.create(j1, n3b);
        Op root = UnionOp.builder().add(j2).add(j3).build();

        assertEquals(Stream.of(j1,j2,j3,root).filter(n -> !isAcyclic(n)).collect(toList()),
                     emptyList());
    }

    @DataProvider
    public static Object[][] intersectResultsData() {
        QueryOp x = new QueryOp(ep, createQuery(TreeUtilsTest.x, knows, Alice));
        QueryOp xy = new QueryOp(ep, createQuery(TreeUtilsTest.x, knows, y));
        QueryOp z = new QueryOp(ep, createQuery(Alice, TreeUtilsTest.z, Bob));
        QueryOp xyz = new QueryOp(ep, createQuery(TreeUtilsTest.x, y, TreeUtilsTest.z));

        return new Object[][] {
                new Object[] {emptyList(), emptySet(), false},
                new Object[] {singleton(x), singleton("x"), false},
                new Object[] {singleton(xyz), asList("x", "y", "z"), false},
                new Object[] {asList(xyz, xyz), asList("x", "y", "z"), false},
                new Object[] {asList(x, x), singleton("x"), false},
                new Object[] {asList(x, z), emptyList(), true},
                new Object[] {asList(z, xy), emptyList(), true},
                new Object[] {asList(x, xy), singleton("x"), true},
                new Object[] {asList(xy, x), singleton("x"), true},
                new Object[] {asList(xyz, xy), asList("x", "y"), true},

                new Object[] {asList(x, z, xyz), emptyList(), true},
                new Object[] {asList(x, xyz, z), emptyList(), true},
                new Object[] {asList(xyz, x, z), emptyList(), true},

                new Object[] {asList(x, xy, xyz), singleton("x"), true},
                new Object[] {asList(x, xyz, xy), singleton("x"), true},
                new Object[] {asList(xyz, x, xy), singleton("x"), true},
        };
    }

    @Test(dataProvider = "intersectResultsData")
    public void testIntersectResults(@Nonnull Collection<Op> list,
                                     @Nonnull Collection<String> expected, boolean dropExpected) {
        AtomicBoolean dropped = new AtomicBoolean();
        Set<String> actual = CollectionUtils.intersect(list, Op::getResultVars, dropped);
        assertEquals(actual, new HashSet<>(expected));
        assertEquals(dropped.get(), dropExpected);
    }

    @DataProvider
    public static Object[][] unionResultsData() {
        QueryOp x = new QueryOp(ep, createQuery(TreeUtilsTest.x, knows, Alice));
        QueryOp xy = new QueryOp(ep, createQuery(TreeUtilsTest.x, knows, y));
        QueryOp z = new QueryOp(ep, createQuery(Alice, TreeUtilsTest.z, Bob));
        QueryOp xyz = new QueryOp(ep, createQuery(TreeUtilsTest.x, y, TreeUtilsTest.z));

        return new Object[][] {
                new Object[] {emptyList(), emptySet()},
                new Object[] {singleton(x), singleton("x")},
                new Object[] {singleton(xyz), asList("x", "y", "z")},
                new Object[] {asList(xyz, xyz), asList("x", "y", "z")},
                new Object[] {asList(x, x), singleton("x")},
                new Object[] {asList(x, z), asList("x", "z")},
                new Object[] {asList(x, xy), asList("x", "y")},
                new Object[] {asList(xy, x), asList("x", "y")},
                new Object[] {asList(xy, x, z), asList("x", "y", "z")},
        };
    }

    @Test(dataProvider = "unionResultsData")
    public void testUnionResults(@Nonnull Collection<Op> list,
                                 @Nonnull Collection<String> expected) {

        assertEquals(CollectionUtils.union(list, Op::getResultVars), new HashSet<>(expected));
    }

    @Test
    public void testBinaryIntersect() {
        List<String> xy = asList("x", "y");
        List<String> xyz = asList("x", "y", "z");
        Set<String> x = singleton("x");

        assertEquals(CollectionUtils.intersect(xy, x), newHashSet("x"));
        assertEquals(CollectionUtils.intersect(x, xy), newHashSet("x"));
        assertEquals(CollectionUtils.intersect(xyz, xy), newHashSet("x", "y"));
        assertEquals(CollectionUtils.intersect(xy, xyz), newHashSet("x", "y"));
        assertEquals(CollectionUtils.intersect(xyz, xyz), newHashSet("x", "y", "z"));
    }

    @DataProvider
    public static Object[][] joinVarsData() {
        Atom atom1 = new Atom("Atom1");
        Atom atom2 = new Atom("Atom2");

        QueryOp xInYZOut = new QueryOp(ep, createQuery(
                x, AtomInputAnnotation.asRequired(atom1, "atom1").get(), y, z));
        QueryOp xyInZOut = new QueryOp(ep, createQuery(
                x, AtomInputAnnotation.asRequired(atom1, "atom1").get(),
                        y, z, AtomInputAnnotation.asRequired(atom2, "atom2").get()));
        QueryOp xKnowsALICE = new QueryOp(ep, createQuery(x, knows, Alice));
        QueryOp xKnowsZ = new QueryOp(ep, createQuery(x, knows, z));
        QueryOp xKnowsY = new QueryOp(ep, createQuery(x, knows, y));

        return new Object[][] {
                new Object[]{xKnowsALICE, xKnowsZ, singleton("x"), emptyList()},
                new Object[]{xKnowsALICE, xInYZOut, singleton("x"), emptyList()},
                new Object[]{xKnowsALICE, xyInZOut, singleton("x"), singleton("y")},
                new Object[]{xKnowsZ, xInYZOut, singleton("x"), emptyList()},
                new Object[]{xKnowsZ, xyInZOut, singleton("x"), singleton("y")},
                new Object[]{xKnowsY, xyInZOut, asList("x", "y"), emptyList()},
                new Object[]{xKnowsY, xInYZOut, singleton("x"), emptyList()},
                new Object[]{xInYZOut, xyInZOut, emptySet(), emptyList()},
        };
    }

    @Test
    public void testDeepCopySingleton() {
        FreeQueryOp root = new FreeQueryOp(createQuery(x, knows, Alice));
        FreeQueryOp expected = new FreeQueryOp(createQuery(x, knows, Alice));
        assertEquals(root.hashCode(), expected.hashCode());
        assertEquals(root, expected);

        int oldHash = root.hashCode();
        Op copy = TreeUtils.deepCopy(root);
        assertEquals(copy.hashCode(), oldHash);
        assertEquals(copy, root);
        assertEquals(copy, expected);

        //mutate copy
        MutableCQuery cQuery = ((FreeQueryOp) copy).getQuery();
        cQuery.add(new Triple(x, age, u));
        ((FreeQueryOp) copy).setQuery(cQuery); //notify change

        //change is visible in copy
        assertEquals(copy.getResultVars(), newHashSet("x", "u"));
        assertEquals(copy.getMatchedTriples(),
                     newHashSet(new Triple(x, knows, Alice), new Triple(x, age, u)));

        //change not visible in root
        assertEquals(root.getResultVars(), singleton("x"));
        assertEquals(root.getMatchedTriples(), singleton(new Triple(x, knows, Alice)));
        assertEquals(root.hashCode(), oldHash);
        assertEquals(root.hashCode(), expected.hashCode());
        assertEquals(root, expected);
        assertNotEquals(copy, root);
    }

    @Test
    public void testDeepCopyTree() {
        Op root = CartesianOp.builder()
                .add(UnionOp.builder()
                        .add(ConjunctionOp.builder()
                                .add(UnionOp.builder()
                                        .add(new FreeQueryOp(createQuery(
                                                Alice, knows, x,
                                                x, age, u1, SPARQLFilter.build("?u1 < 23"))))
                                        .add(new FreeQueryOp(createQuery(
                                                Bob, knows, x,
                                                x, age, u2, SPARQLFilter.build("?u2 < 23"))))
                                        .build())
                                .add(new FreeQueryOp(createQuery(
                                        x, knows, y,
                                        y, age,   u3, SPARQLFilter.build("?u3 > 27"))))
                                .build())
                        .add(new FreeQueryOp(createQuery(
                                x, knows, Charlie,
                                x, age, v3,
                                SPARQLFilter.build("?v3 < 17"))))
                        .build())
                .add(new FreeQueryOp(createQuery(z, age, o3, SPARQLFilter.build("?o3 > 31"))))
                .build();
        Op expected = CartesianOp.builder()
                .add(UnionOp.builder()
                        .add(ConjunctionOp.builder()
                                .add(UnionOp.builder()
                                        .add(new FreeQueryOp(createQuery(
                                                Alice, knows, x,
                                                x, age, u1, SPARQLFilter.build("?u1 < 23"))))
                                        .add(new FreeQueryOp(createQuery(
                                                Bob, knows, x,
                                                x, age, u2, SPARQLFilter.build("?u2 < 23"))))
                                        .build())
                                .add(new FreeQueryOp(createQuery(
                                        x, knows, y,
                                        y, age,   u3, SPARQLFilter.build("?u3 > 27"))))
                                .build())
                        .add(new FreeQueryOp(createQuery(
                                x, knows, Charlie,
                                x, age, v3,
                                SPARQLFilter.build("?v3 < 17"))))
                        .build())
                .add(new FreeQueryOp(createQuery(z, age, o3, SPARQLFilter.build("?o3 > 31"))))
                .build();
        assertEquals(root.hashCode(), expected.hashCode());
        assertEquals(root.getMatchedTriples(), expected.getMatchedTriples());
        assertEquals(root.getResultVars(), expected.getResultVars());

        int hashCode = root.hashCode();
        Op copy = TreeUtils.deepCopy(root);
        assertEquals(copy.hashCode(), hashCode);
        assertEquals(copy.getMatchedTriples(), root.getMatchedTriples());
        assertEquals(copy.getMatchedTriples(), expected.getMatchedTriples());
        assertEquals(copy.getResultVars(), root.getResultVars());
        assertEquals(copy.getResultVars(), expected.getResultVars());
        assertEquals(streamPreOrder(copy).flatMap(o -> o.modifiers().stream()).collect(toSet()),
                     streamPreOrder(root).flatMap(o -> o.modifiers().stream()).collect(toSet()));
        assertEquals(streamPreOrder(copy).flatMap(o -> o.modifiers().stream()).collect(toSet()),
                     streamPreOrder(expected).flatMap(o -> o.modifiers().stream()).collect(toSet()));
        assertEquals(copy, root);
        assertEquals(copy, expected);

        // remove all filters ....
        streamPreOrder(copy).forEach(o -> o.modifiers().removeIf(SPARQLFilter.class::isInstance));

        assertNotEquals(copy, expected);
        assertEquals(streamPreOrder(root).flatMap(o -> o.modifiers().stream()).collect(toSet()),
                     streamPreOrder(expected).flatMap(o -> o.modifiers().stream()).collect(toSet()));
        assertEquals(root, expected); //root was not affected

        //remove a triple from a leaf -- compute expected sets
        HashSet<String> rootResultVars = new HashSet<>(root.getResultVars());
        HashSet<String> expectedResultVars = new HashSet<>(rootResultVars);
        assertTrue(expectedResultVars.remove("u1"));
        HashSet<Triple> rootMatchedTriples = new HashSet<>(root.getMatchedTriples());
        HashSet<Triple> expectedMatchedTriples = new HashSet<>(rootMatchedTriples);
        assertTrue(expectedMatchedTriples.remove(new Triple(x, age, u1)));

        //remove a triple from a leaf -- do it
        FreeQueryOp op = (FreeQueryOp)copy.getChildren().get(0) //union
                .getChildren().get(0) //conjunction
                .getChildren().get(0) //union
                .getChildren().get(0); //query
        MutableCQuery cQuery = op.getQuery();
        assertEquals(cQuery.remove(1), new Triple(x, age, u1));
        op.setQuery(cQuery); //notify change

        //change is visible on copy tree
        assertEquals(op.getResultVars(), singleton("x"));
        assertEquals(copy.getResultVars(), expectedResultVars);
        assertEquals(copy.getMatchedTriples(), expectedMatchedTriples);
        assertNotEquals(copy, expected);

        //change is NOT visible on original tree
        FreeQueryOp rootLeaf = (FreeQueryOp) root.getChildren().get(0) //union
                .getChildren().get(0) // conjunction
                .getChildren().get(0) // union
                .getChildren().get(0);//query
        assertEquals(rootLeaf.getResultVars(), newHashSet("x", "u1"));
        assertEquals(root.getResultVars(), rootResultVars);
        assertEquals(root.getMatchedTriples(), rootMatchedTriples);
        assertEquals(root, expected); //root was not affected
    }
}