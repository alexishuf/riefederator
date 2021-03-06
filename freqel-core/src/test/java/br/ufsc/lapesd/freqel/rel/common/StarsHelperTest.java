package br.ufsc.lapesd.freqel.rel.common;

import br.ufsc.lapesd.freqel.TestContext;
import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.query.CQuery;
import com.google.common.collect.Sets;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static br.ufsc.lapesd.freqel.query.parse.CQueryContext.createQuery;
import static org.testng.Assert.assertEquals;

@Test(groups = {"fast"})
public class StarsHelperTest implements TestContext {
    @Test
    public void testFindStars() {
        CQuery query = createQuery(x, name,   u, x, knows, o,
                                   y, name,   u, y, age,   lit(22),
                                   z, author, w, z, sameAs, y);
        List<Triple> tripleList = query.asList();

        List<StarSubQuery> stars = StarsHelper.findStars(query);

        Set<Set<Triple>> expected = new HashSet<>(), actual = new HashSet<>();
        expected.add(Sets.newHashSet(tripleList.get(0), tripleList.get(1)));
        expected.add(Sets.newHashSet(tripleList.get(2), tripleList.get(3)));
        expected.add(Sets.newHashSet(tripleList.get(4), tripleList.get(5)));
        for (StarSubQuery star : stars)
            actual.add(new HashSet<>(star.getTriples()));
        assertEquals(actual, expected);
    }
}