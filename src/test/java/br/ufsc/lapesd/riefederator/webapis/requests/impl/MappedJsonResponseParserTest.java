package br.ufsc.lapesd.riefederator.webapis.requests.impl;

import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.model.term.Lit;
import br.ufsc.lapesd.riefederator.model.term.Term;
import br.ufsc.lapesd.riefederator.model.term.URI;
import br.ufsc.lapesd.riefederator.model.term.Var;
import br.ufsc.lapesd.riefederator.model.term.std.StdURI;
import br.ufsc.lapesd.riefederator.model.term.std.StdVar;
import br.ufsc.lapesd.riefederator.query.CQEndpoint;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.Results;
import br.ufsc.lapesd.riefederator.query.Solution;
import br.ufsc.lapesd.riefederator.query.impl.MapSolution;
import com.google.common.collect.Sets;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static br.ufsc.lapesd.riefederator.jena.JenaWrappers.fromJena;
import static java.util.Collections.singleton;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static org.testng.Assert.*;

public class MappedJsonResponseParserTest {
    private static final @Nonnull String EX = "http://example.org/";
    private static final @Nonnull String AEX = "http://auto.example.org/";
    private static final @Nonnull URI pa = ex("p/a");
    private static final @Nonnull URI pb = ex("p/b");
    private static final @Nonnull Var X = new StdVar("x");
    private static final @Nonnull Var Y = new StdVar("y");
    private static final @Nonnull Var Z = new StdVar("z");
    private static final @Nonnull Lit i23 = fromJena(createTypedLiteral(23));
    private static final @Nonnull Lit i27 = fromJena(createTypedLiteral(27));
    private static final @Nonnull Lit i31 = fromJena(createTypedLiteral(31));

    private MappedJsonResponseParser parser;
    private MappedJsonResponseParser parserWithPrefix;

    private static @Nonnull StdURI ex(@Nonnull String local) {
        return new StdURI(EX+local);
    }

    @BeforeMethod
    public void setUp() {
        Map<String, String> map = new HashMap<>();
        map.put("prop_a", EX + "p/a");
        map.put("prop_b", EX + "p/b");
        parser = new MappedJsonResponseParser(map);
        parserWithPrefix = new MappedJsonResponseParser(map, AEX);
    }

    @AfterMethod
    public void tearDown() {
        parser = null;
    }

    @DataProvider
    public  static Object[][] emptyData() {
        return new Object[][] {
                new Object[] {""},
                new Object[] {"{}"},
                new Object[] {"{\"p_a\": 1, \"p_b\": 2}"},
                new Object[] {"[]"},
                new Object[] {"[{\"p_a\": 1, \"p_b\": 2}]"},
        };
    }

    @Test(dataProvider = "emptyData")
    public void testEmptyString(String json) {
        CQEndpoint ep = parser.parse(json, EX + "res/1");
        assertNotNull(ep);
        try (Results results = ep.query(CQuery.from(new Triple(X, Y, Z)))) {
            assertFalse(results.hasNext());
        }
    }

    @Test
    public void testPlainWithExtras() {
        CQEndpoint ep = parser.parse("{\"prop_a\": 1, \"prop_c\": 2}", EX + "res/1");
        Triple triple = new Triple(X, Y, Z);
        Set<Solution> all = new HashSet<>();
        try (Results results = ep.query(CQuery.from(triple))) {
            results.forEachRemaining(all::add);
        }

        Set<MapSolution> expected = singleton(MapSolution.builder()
                .put(X, ex("res/1"))
                .put(Y, pa)
                .put(Z, fromJena(createTypedLiteral(1))).build());
        assertEquals(all, expected);
    }

    @Test
    public void testBlankNode() {
        CQEndpoint ep = parser.parse("{\"prop_a\": {\"prop_b\": 23}}", EX + "res/1");

        try (Results r1 = ep.query(CQuery.from(new Triple(ex("res/1"), pa, X)))) {
            assertTrue(r1.hasNext());
            Solution next = r1.next();
            Term x = next.get(X.getName());
            assertNotNull(x);
            assertTrue(x.isBlank());
        }

        try (Results r2 = ep.query(CQuery.from(new Triple(X, pb, i23)))) {
            assertTrue(r2.hasNext());
            Term x = r2.next().get(X.getName());
            assertNotNull(x);
            assertTrue(x.isBlank());
        }

        Set<Solution> all = new HashSet<>();
        try (Results r3 = ep.query(new Triple(X, Y, Z))) {
            r3.forEachRemaining(all::add);
        }
        assertEquals(all.size(), 2);
    }

    @Test
    public void testHAL() {
        CQEndpoint ep = parser.parse("{\n" +
                "  \"prop_a\": {\n" +
                "    \"_links\": {\n" +
                "      \"self\": {\n" +
                "        \"href\": \"http://example.org/res/23\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"prop_b\": 23\n" +
                "  }\n" +
                "}\n", EX + "res/2");
        Set<Solution> all = new HashSet<>();
        try (Results results = ep.query(CQuery.from(new Triple(X, Y, Z)))) {
            results.forEachRemaining(all::add);
        }

        StdURI r23 = ex("res/23");
        HashSet<MapSolution> expected = Sets.newHashSet(
                MapSolution.builder().put(X, ex("res/2")).put(Y, pa).put(Z, r23).build(),
                MapSolution.builder().put(X, r23).put(Y, pb).put(Z, i23).build());
        assertEquals(all, expected);
    }

    @Test
    public void testHALOverrides() {
        CQEndpoint ep = parser.parse("{\n" +
                "  \"prop_a\": 23,\n" +
                "  \"_links\": {\n" +
                "    \"self\": {\n" +
                "      \"href\": \"http://example.org/res/37\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n", EX + "res/1");
        Set<Solution> all = new HashSet<>();
        try (Results results = ep.query(CQuery.from(new Triple(X, Y, Z)))) {
            results.forEachRemaining(all::add);
        }

        assertEquals(all, singleton(
                MapSolution.builder().put(X, ex("res/37")).put(Y, pa).put(Z, i23).build()));
    }

    @Test
    public void testFallbackToPrefixOnEmbedded() {
        CQEndpoint ep = parserWithPrefix.parse("{\n" +
                "  \"prop_a\": 23,\n" +
                "  \"_embedded\": {\n" +
                "    \"prop_b\": {\n" +
                "      \"_links\": {\n" +
                "        \"self\": {\n" +
                "          \"href\": \"http://example.org/res/5\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"prop_a\": 27,\n" +
                "      \"prop_c\": 31\n" +
                "    }\n" +
                "  }\n" +
                "}\n", EX + "res/1");
        Set<Solution> all = new HashSet<>();
        try (Results results = ep.query(CQuery.from(new Triple(X, Y, Z)))) {
            results.forEachRemaining(all::add);
        }

        StdURI pc = new StdURI(AEX + "prop_c");
        HashSet<Solution> expected = Sets.newHashSet(
                MapSolution.builder().put(X, ex("res/1")).put(Y, pa).put(Z, i23).build(),
                MapSolution.builder().put(X, ex("res/1")).put(Y, pb).put(Z, ex("res/5")).build(),
                MapSolution.builder().put(X, ex("res/5")).put(Y, pa).put(Z, i27).build(),
                MapSolution.builder().put(X, ex("res/5")).put(Y, pc).put(Z, i31).build()
        );
        assertEquals(all, expected);
    }
}