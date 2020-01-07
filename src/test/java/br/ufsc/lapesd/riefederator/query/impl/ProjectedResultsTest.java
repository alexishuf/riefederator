package br.ufsc.lapesd.riefederator.query.impl;

import br.ufsc.lapesd.riefederator.model.term.std.StdURI;
import br.ufsc.lapesd.riefederator.query.Cardinality;
import br.ufsc.lapesd.riefederator.query.Solution;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.*;

import static com.google.common.collect.Sets.newHashSet;
import static org.testng.Assert.*;

public class ProjectedResultsTest {
    private static @Nonnull StdURI ex(@Nonnull String local) {
        return new StdURI("http://example.org/"+local);
    }

    private static @Nonnull CollectionResults createResults(int count, String... names) {
        List<Solution> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MapSolution.Builder builder = MapSolution.builder();
            for (String name : names) builder.put(name, ex(name+i));
            list.add(builder.build());
        }
        return new CollectionResults(list, new HashSet<>(Arrays.asList(names)));
    }

    @Test
    public void testEmpty() {
        CollectionResults in = createResults(0, "x", "y");
        ProjectingResults p = new ProjectingResults(in, newHashSet("x"));
        assertEquals(p.getVarNames(), newHashSet("x"));
        assertEquals(p.getReadyCount(), 0);
        assertEquals(p.getCardinality(), Cardinality.EMPTY);
    }

    @Test
    public void testBadVariables() {
        CollectionResults in = createResults(0, "x", "y");
        expectThrows(IllegalArgumentException.class,
                () -> new ProjectingResults(in, newHashSet("x", "z")));
    }

    @Test
    public void testProjectAll() {
        CollectionResults in = createResults(1, "x", "y");
        ProjectingResults p = new ProjectingResults(in, newHashSet("x", "y"));

        assertTrue(p.hasNext());
        assertEquals(p.getCardinality(), Cardinality.exact(1));
        assertEquals(p.next(), MapSolution.builder().put("x", ex("x0"))
                                                    .put("y", ex("y0")).build());
        assertFalse(p.hasNext());
        assertEquals(p.getCardinality(), Cardinality.EMPTY);
    }

    @Test
    public void testProjectNonEmpty() {
        CollectionResults in = createResults(2, "x", "y");
        ProjectingResults p = new ProjectingResults(in, Collections.singleton("x"));

        assertTrue(p.hasNext());
        assertEquals(p.getCardinality(), Cardinality.exact(2));
        assertEquals(p.next(), MapSolution.build("x", ex("x0")));

        assertTrue(p.hasNext());
        assertEquals(p.getCardinality(), Cardinality.exact(1));
        assertEquals(p.next(), MapSolution.build("x", ex("x1")));

        assertFalse(p.hasNext());
        assertEquals(p.getCardinality(), Cardinality.EMPTY);
    }

}