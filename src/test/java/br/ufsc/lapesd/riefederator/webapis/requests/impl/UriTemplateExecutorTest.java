package br.ufsc.lapesd.riefederator.webapis.requests.impl;

import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.model.term.Lit;
import br.ufsc.lapesd.riefederator.model.term.URI;
import br.ufsc.lapesd.riefederator.model.term.Var;
import br.ufsc.lapesd.riefederator.model.term.std.StdLit;
import br.ufsc.lapesd.riefederator.model.term.std.StdURI;
import br.ufsc.lapesd.riefederator.model.term.std.StdVar;
import br.ufsc.lapesd.riefederator.query.CQEndpoint;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.Results;
import br.ufsc.lapesd.riefederator.query.Solution;
import br.ufsc.lapesd.riefederator.query.impl.MapSolution;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.XSD;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTestNg;
import org.glassfish.jersey.uri.UriTemplate;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

import static java.util.Collections.singleton;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.*;

public class UriTemplateExecutorTest extends JerseyTestNg.ContainerPerClassTest {
    private static final String EX = "http://example.org/";
    private static final @Nonnull URI ALICE = new StdURI(EX+"Alice");
    private static final @Nonnull URI BOB = new StdURI(EX+"Bob");
    private static final @Nonnull URI knows = new StdURI(FOAF.knows.getURI());
    private static final @Nonnull URI op1 = new StdURI(EX+"op1");
    private static final @Nonnull URI op2 = new StdURI(EX+"op2");
    private static final @Nonnull URI result = new StdURI(EX+"result");
    private static final @Nonnull URI xint = new StdURI(XSD.xint.getURI());
    private static final @Nonnull Lit i2 = StdLit.fromEscaped("2", xint);
    private static final @Nonnull Lit i3 = StdLit.fromEscaped("3", xint);
    private static final @Nonnull Lit i5 = StdLit.fromEscaped("5", xint);
    private static final @Nonnull Var X = new StdVar("x");
    private static final @Nonnull Var Y = new StdVar("y");
    private static final @Nonnull Var Z = new StdVar("z");

    @Path("/")
    public static class Service {
        @GET
        @Path("rdf/{name:.*}")
        public Model getRDF(@PathParam("name") String name) {
            Model model = ModelFactory.createDefaultModel();
            InputStream in = Service.class.getResourceAsStream("../../../" + name);
            RDFDataMgr.read(model, in, Lang.TTL);
            return model;
        }

        @GET
        @Path("sum/{x}/{y}")
        public String sum(@PathParam("x") int x, @PathParam("y") int y,
                          @Context UriInfo uriInfo) {
            return "{\n" +
                   "  \"op1\": "+x+",\n" +
                   "  \"op2\": "+y+",\n" +
                   "  \"result\": "+(x+y)+",\n" +
                   "  \"_links\": {\n" +
                   "    \"self\": {\n" +
                   "      \"href\": \""+uriInfo.getAbsolutePath()+"\"\n" +
                   "    }\n" +
                   "  }\n" +
                   "}\n";
        }
    }

    @Override
    protected Application configure() {
        return new ResourceConfig().register(ModelMessageBodyWriter.class).register(Service.class);
    }


    @Test
    public void selfTestRdfService() {
        String path = "/rdf/rdf-1.nt";
        String json = target(path).request("application/ld+json").get(String.class);
        Model actual = ModelFactory.createDefaultModel();
        RDFDataMgr.read(actual, new StringReader(json), null, Lang.JSONLD);

        Model expected = ModelFactory.createDefaultModel();
        InputStream in = getClass().getResourceAsStream("../../../rdf-1.nt");
        RDFDataMgr.read(expected, in, Lang.NT);

        assertTrue(expected.isIsomorphicWith(actual));
    }

    @Test
    public void selfTestSumService() {
        String json = target("sum/3/5").request(APPLICATION_JSON).get(String.class);
        JsonElement root = new JsonParser().parse(json);
        assertEquals(root.getAsJsonObject().get("result").toString(), "8");
    }

    @Test
    public void testRdfService() {
        String template = target().getUri().toString() + "rdf/{file}";
        UriTemplateExecutor exec = new UriTemplateExecutor(new UriTemplate(template));
        StdLit literal = StdLit.fromEscaped("rdf-1.nt", "en");
        Iterator<? extends CQEndpoint> it = exec.execute(MapSolution.build("file", literal));
        assertTrue(it.hasNext());

        Set<Solution> all = new HashSet<>();
        try (CQEndpoint ep = it.next();
             Results results = ep.query(CQuery.from(new Triple(ALICE, knows, X)))) {
            results.forEachRemaining(all::add);
        }
        assertEquals(all, singleton(MapSolution.build(X, BOB)));
        assertFalse(it.hasNext());
    }

    @Test
    public void testJsonMappedService() {
        String template = target().getUri().toString() + "sum/{x}/{y}";
        Map<String, String> map = new HashMap<>();
        map.put("op1", op1.getURI());
        map.put("op2", op2.getURI());
        MappedJsonResponseParser jsonParser = new MappedJsonResponseParser(map, EX);
        UriTemplateExecutor exec = UriTemplateExecutor.from(new UriTemplate(template))
                .withResponseParser(jsonParser).build();

        Iterator<? extends CQEndpoint> it;
        it = exec.execute(MapSolution.builder().put(X, i2).put(Y, i3).build());
        assertTrue(it.hasNext());

        Set<Solution> all = new HashSet<>();
        try (CQEndpoint ep = it.next();
             Results results = ep.query(CQuery.from(new Triple(X, Y, Z)))) {
            results.forEachRemaining(all::add);
        }

        StdURI res = new StdURI(new UriTemplate(template).createURI("2", "3"));
        Set<Solution> expected = Sets.newHashSet(
                MapSolution.builder().put(X, res).put(Y, op1   ).put(Z, i2).build(),
                MapSolution.builder().put(X, res).put(Y, op2   ).put(Z, i3).build(),
                MapSolution.builder().put(X, res).put(Y, result).put(Z, i5).build()
        );
        assertEquals(all, expected);
    }

}