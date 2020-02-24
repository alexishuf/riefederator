package br.ufsc.lapesd.riefederator.webapis.description;

import br.ufsc.lapesd.riefederator.TestContext;
import br.ufsc.lapesd.riefederator.description.CQueryMatch;
import br.ufsc.lapesd.riefederator.description.MatchAnnotation;
import br.ufsc.lapesd.riefederator.description.Molecule;
import br.ufsc.lapesd.riefederator.description.molecules.Atom;
import br.ufsc.lapesd.riefederator.description.semantic.SemanticCQueryMatch;
import br.ufsc.lapesd.riefederator.model.Triple;
import br.ufsc.lapesd.riefederator.model.term.Lit;
import br.ufsc.lapesd.riefederator.model.term.URI;
import br.ufsc.lapesd.riefederator.model.term.std.StdLit;
import br.ufsc.lapesd.riefederator.model.term.std.StdURI;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.reason.tbox.TBoxSpec;
import br.ufsc.lapesd.riefederator.reason.tbox.TransitiveClosureTBoxReasoner;
import br.ufsc.lapesd.riefederator.webapis.requests.impl.UriTemplateExecutor;
import com.google.common.collect.Sets;
import org.glassfish.jersey.uri.UriTemplate;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static br.ufsc.lapesd.riefederator.reason.tbox.OWLAPITBoxReasoner.structural;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.testng.Assert.*;

public class APIMoleculeMatcherTest implements TestContext {
    public static final @Nonnull String EX = "http://example.org/";
    public static final @Nonnull URI author1 = new StdURI(EX+"authors/1");
    public static final @Nonnull URI city1 = new StdURI(EX+"cities/1");
    public static final @Nonnull Lit title1 = StdLit.fromEscaped("title1", "en");
    public static final @Nonnull Lit title2 = StdLit.fromEscaped("title2", "en");
    public static final @Nonnull Lit crime = StdLit.fromUnescaped("Crime");
    public static final @Nonnull Lit authorName1 = StdLit.fromEscaped("name1", "en");

    public static final @Nonnull APIMolecule BOOKS_BY_AUTHOR, BOOK_CITATIONS, AM_BOOK_CITATIONS;
    public static final @Nonnull Atom AUTHOR, AUTHOR_NAME, BOOK_TITLE, CITED_BOOK,
                                      AM_CITED_BOOK, CITED_BOOK_TITLE;

    public static final @Nonnull APIMolecule BOOKS_BY_MAIN_AUTHOR;
    public static final @Nonnull Atom MAIN_AUTHOR;

    static {
        AUTHOR_NAME = Molecule.builder("authorName").buildAtom();
        BOOK_TITLE = Molecule.builder("bookTitle").buildAtom();
        CITED_BOOK_TITLE = Molecule.builder("citedBookTitle").buildAtom();
        AUTHOR = Molecule.builder("Author").out(authorName, AUTHOR_NAME).exclusive().buildAtom();
        Molecule molecule = Molecule.builder("Book")
                .out(title, BOOK_TITLE)
                .out(author, AUTHOR)
                .exclusive()
                .build();
        Map<String, String> atom2var = new HashMap<>();
        atom2var.put("authorName", "hasAuthorName");
        UriTemplateExecutor exec = createExecutor("/books/{?hasAuthorName}");
        BOOKS_BY_AUTHOR = new APIMolecule(molecule, exec, atom2var);

        CITED_BOOK = Molecule.builder("CitedBook")
                .out(title, CITED_BOOK_TITLE)
                .out(author, AUTHOR)
                .exclusive()
                .buildAtom();
        molecule = Molecule.builder("Book")
                .out(title, BOOK_TITLE)
                .out(author, AUTHOR)
                .out(cites, CITED_BOOK)
                .exclusive()
                .build();
        atom2var.clear();
        atom2var.put(BOOK_TITLE.getName(), "hasTitle");
        exec = createExecutor("/books/citations/{?hasTitle}");
        BOOK_CITATIONS = new APIMolecule(molecule, exec, atom2var);

        AM_CITED_BOOK = Molecule.builder("CitedBook")
                .out(title, BOOK_TITLE)
                .out(author, AUTHOR)
                .exclusive()
                .buildAtom();
        molecule = Molecule.builder("Book")
                .out(title, BOOK_TITLE)
                .out(author, AUTHOR)
                .out(cites, AM_CITED_BOOK)
                .exclusive()
                .build();
        atom2var.clear();
        atom2var.put(BOOK_TITLE.getName(), "hasTitle");
        exec = createExecutor("/books/citations/{?hasTitle}");
        AM_BOOK_CITATIONS = new APIMolecule(molecule, exec, atom2var);

        /* ~~~ molecules used in testSemanticMatch() ~~~ */

        MAIN_AUTHOR = Molecule.builder("MainAuthor")
                .out(authorName, AUTHOR_NAME)
                .exclusive().buildAtom();
        molecule = Molecule.builder("Book")
                .out(title, BOOK_TITLE)
                .out(mainAuthor, MAIN_AUTHOR)
                .exclusive().build();
        exec = createExecutor("/books/{?hasMainAuthorName}");
        atom2var.clear();
        atom2var.put(AUTHOR_NAME.getName(), "hasMainAuthorName");
        BOOKS_BY_MAIN_AUTHOR = new APIMolecule(molecule, exec, atom2var);
    }

    private static @Nonnull UriTemplateExecutor createExecutor(String path) {
        return new UriTemplateExecutor(new UriTemplate(EX + path));
    }

    @DataProvider
    public static Object[][] matchData() {
        List<CQuery> e = emptyList();
        return Stream.of(
                asList(BOOKS_BY_AUTHOR, singleton(new Triple(x, author, author1)), e),
                asList(BOOKS_BY_AUTHOR, singleton(new Triple(x, authorName, y)), e),
                asList(BOOKS_BY_AUTHOR, singleton(new Triple(x, authorName, authorName1)),
                       singleton(CQuery.builder()
                               .add(new Triple(x, authorName, authorName1))
                               .annotate(x, AtomAnnotation.of(AUTHOR))
                               .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                               .build())),
                asList(BOOKS_BY_AUTHOR,
                       asList(new Triple(x, author, y), new Triple(y, authorName, authorName1)),
                       singleton(CQuery.builder()
                                .add(new Triple(x, author, y),
                                     new Triple(y, authorName, authorName1))
                                .annotate(x, AtomAnnotation.of(BOOKS_BY_AUTHOR.getMolecule().getCore()))
                                .annotate(y, AtomAnnotation.of(AUTHOR))
                                .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                                .build())),
                asList(BOOKS_BY_AUTHOR,
                       asList(new Triple(x, bornIn, city1),
                              new Triple(x, name, y),
                              new Triple(z, author, w),
                              new Triple(w, authorName, y)),
                       singleton(CQuery.with(asList(new Triple(z, author, w),
                                                    new Triple(w, authorName, y)))
                                       .annotate(z, AtomAnnotation.of(BOOKS_BY_AUTHOR.getMolecule().getCore()))
                                       .annotate(w, AtomAnnotation.of(AUTHOR))
                                       .annotate(y, AtomAnnotation.asRequired(AUTHOR_NAME))
                                       .build()
                               )),
                asList(BOOK_CITATIONS, singleton(new Triple(x, title, title1)),
                        singleton(CQuery.with(new Triple(x, title, title1))
                                .annotate(x, AtomAnnotation.of(BOOK_CITATIONS.getMolecule().getCore()))
                                .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE))
                                .build())),
                asList(BOOK_CITATIONS, asList(new Triple(x, title, title1),
                                              new Triple(x, cites, y),
                                              new Triple(y, author, author1)),
                        singleton(CQuery.with(new Triple(x, title, title1),
                                              new Triple(x, cites, y),
                                              new Triple(y, author, author1))
                                .annotate(x, AtomAnnotation.of(BOOK_CITATIONS.getMolecule().getCore()))
                                .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE))
                                .annotate(y, AtomAnnotation.of(CITED_BOOK))
                                .annotate(author1, AtomAnnotation.of(AUTHOR))
                                .build())),
                asList(BOOK_CITATIONS, asList(new Triple(x, title, title1),
                                              new Triple(x, cites, y),
                                              new Triple(y, title, z)),
                        asList(CQuery.with(new Triple(x, title, title1),
                                           new Triple(x, cites, y),
                                           new Triple(y, title, z))
                                        .annotate(x, AtomAnnotation.of(BOOK_CITATIONS.getMolecule().getCore()))
                                        .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE))
                                        .annotate(y, AtomAnnotation.of(CITED_BOOK))
                                        .annotate(z, AtomAnnotation.of(CITED_BOOK_TITLE))
                                        .build(),
                               CQuery.with(new Triple(y, title, z))
                                       .annotate(y, AtomAnnotation.of(BOOK_CITATIONS.getMolecule().getCore()))
                                       .annotate(z, AtomAnnotation.asRequired(BOOK_TITLE))
                                       .build())),
                asList(AM_BOOK_CITATIONS, asList(new Triple(x, title, title1),
                                                 new Triple(x, cites, y),
                                                 new Triple(y, title, title2)),
                       asList(CQuery.with(new Triple(x, title, title1), new Triple(x, cites, y))
                                    .annotate(x, AtomAnnotation.of(AM_BOOK_CITATIONS.getMolecule().getCore()))
                                    .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE))
                                    .annotate(y, AtomAnnotation.of(AM_CITED_BOOK)).build(),
                              CQuery.with(new Triple(x, cites, y), new Triple(y, title, title2))
                                    .annotate(x, AtomAnnotation.of(AM_BOOK_CITATIONS.getMolecule().getCore()))
                                    .annotate(y, AtomAnnotation.of(AM_CITED_BOOK))
                                    .annotate(title2, AtomAnnotation.asRequired(BOOK_TITLE)).build(),
                              CQuery.with(new Triple(x, title, title1))
                                    .annotate(x, AtomAnnotation.of(AM_CITED_BOOK))
                                    .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE)).build(),
                              CQuery.with(new Triple(y, title, title2))
                                    .annotate(y, AtomAnnotation.of(AM_BOOK_CITATIONS.getMolecule().getCore()))
                                    .annotate(title2, AtomAnnotation.asRequired(BOOK_TITLE)).build()
                       )), // ambiguity does not allow a single EG
                asList(AM_BOOK_CITATIONS, asList(new Triple(x, title, title1),
                                                 new Triple(x, cites, y),
                                                 new Triple(y, title, z)),
                        asList(CQuery.with(new Triple(x, title, title1), new Triple(x, cites, y))
                                        .annotate(x, AtomAnnotation.of(AM_BOOK_CITATIONS.getMolecule().getCore()))
                                        .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE))
                                        .annotate(y, AtomAnnotation.of(AM_CITED_BOOK)).build(),
                               CQuery.with(new Triple(x, cites, y), new Triple(y, title, z))
                                        .annotate(x, AtomAnnotation.of(AM_BOOK_CITATIONS.getMolecule().getCore()))
                                        .annotate(y, AtomAnnotation.of(AM_CITED_BOOK))
                                        .annotate(z, AtomAnnotation.asRequired(BOOK_TITLE)).build(),
                               CQuery.with(new Triple(x, title, title1))
                                        .annotate(x, AtomAnnotation.of(AM_CITED_BOOK))
                                        .annotate(title1, AtomAnnotation.asRequired(BOOK_TITLE)).build(),
                               CQuery.with(new Triple(y, title, z))
                                        .annotate(y, AtomAnnotation.of(AM_BOOK_CITATIONS.getMolecule().getCore()))
                                        .annotate(z, AtomAnnotation.asRequired(BOOK_TITLE)).build()
                        )) // ambiguity does not allow a single EG
        ).map(List::toArray).toArray(Object[][]::new);
    }

    @Test(dataProvider = "matchData")
    public void testMatch(APIMolecule apiMolecule, Collection<Triple> query,
                          Collection<CQuery> egs) {
        APIMoleculeMatcher matcher = new APIMoleculeMatcher(apiMolecule, structural());
        CQueryMatch match = matcher.match(CQuery.from(query));
        assertCompatible(match.getKnownExclusiveGroups(), egs);
    }

    private void assertCompatible(Collection<CQuery> actual,
                                  Collection<CQuery> expected) {
        if (actual == expected)
            return; //same
        assertEquals(actual.size(), expected.size());
        for (CQuery eg : expected) {
            boolean ok = actual.stream().anyMatch(a -> {
                if (!a.getSet().equals(eg.getSet()))
                    return false;
                boolean[] annOk = {true};
                eg.forEachTermAnnotation((term, ann) -> {
                    if (!a.getTermAnnotations(term).contains(ann))
                        annOk[0] = false;
                });
                eg.forEachTripleAnnotation((triple, ann) -> {
                    if (!a.getTripleAnnotations(triple).contains(ann))
                        annOk[0] = false;
                });
                return annOk[0];
            });
            assertTrue(ok, "Expected EG "+eg+" missing in actual");
        }
    }

    @DataProvider
    public static @Nonnull Object[][] semanticMatchData() {
        List<Object[]> plain = new ArrayList<>();
        for (Object[] row : matchData()) {
            ArrayList<Object> filled = new ArrayList<>(asList(row));
            assertEquals(row.length, 3);
            List<Set<CQuery>> alternativeSets = new ArrayList<>();
            for (int i = 0; i < ((Collection<?>) row[2]).size(); i++)
                alternativeSets.add(null);
            filled.add(alternativeSets);
            plain.add(filled.toArray());
        }
        List<Object[]> semantic= Stream.of(
            asList(BOOKS_BY_MAIN_AUTHOR, asList(new Triple(x, mainAuthor, y),
                                                new Triple(y, authorName, authorName1)),
                    singletonList(CQuery.with(new Triple(x, mainAuthor, y),
                                         new Triple(y, authorName, authorName1))
                           .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                           .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                           .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                           .build()),
                    singletonList(null)),
            asList(BOOKS_BY_MAIN_AUTHOR, asList(new Triple(x, author, y),
                                                new Triple(y, authorName, authorName1)),
                    singletonList(CQuery.with(new Triple(x, author, y),
                                              new Triple(y, authorName, authorName1))
                            .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                            .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                            .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                            .build()),
                    singletonList(singleton(CQuery.with(new Triple(x, mainAuthor, y),
                                                        new Triple(y, authorName, authorName1))
                            .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                            .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                            .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                            .annotate(new Triple(x, mainAuthor, y),
                                      new MatchAnnotation(new Triple(x, author, y)))
                            .build()))),
            asList(BOOKS_BY_MAIN_AUTHOR, asList(new Triple(x, title, z),
                                                new Triple(x, author, y),
                                                new Triple(y, authorName, authorName1)),
                    singletonList(CQuery.with(new Triple(x, title, z),
                                              new Triple(x, author, y),
                                              new Triple(y, authorName, authorName1))
                            .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                            .annotate(z, AtomAnnotation.of(BOOK_TITLE))
                            .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                            .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                            .build()),
                    singletonList(singleton(CQuery.with(new Triple(x, title, z),
                                                        new Triple(x, mainAuthor, y),
                                                        new Triple(y, authorName, authorName1))
                            .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                            .annotate(z, AtomAnnotation.of(BOOK_TITLE))
                            .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                            .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                            .annotate(new Triple(x, mainAuthor, y),
                                      new MatchAnnotation(new Triple(x, author, y)))
                            .build()))),
            asList(BOOKS_BY_MAIN_AUTHOR, asList(new Triple(x, title,  title1),
                                                new Triple(x, author, y),
                                                new Triple(y, authorName, authorName1)),
                    singletonList(CQuery.with(new Triple(x, title,  title1),
                                          new Triple(x, author, y),
                                          new Triple(y, authorName, authorName1))
                            .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                            .annotate(title1, AtomAnnotation.of(BOOK_TITLE))
                            .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                            .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                            .build()),
                    singletonList(singleton(CQuery.with(new Triple(x, title,  title1),
                                                        new Triple(x, mainAuthor, y),
                                                        new Triple(y, authorName, authorName1))
                            .annotate(x, AtomAnnotation.of(BOOKS_BY_MAIN_AUTHOR.getMolecule().getCore()))
                            .annotate(title1, AtomAnnotation.of(BOOK_TITLE))
                            .annotate(y, AtomAnnotation.of(MAIN_AUTHOR))
                            .annotate(authorName1, AtomAnnotation.asRequired(AUTHOR_NAME))
                            .annotate(new Triple(x, mainAuthor, y),
                                      new MatchAnnotation(new Triple(x, author, y)))
                            .build()))),
            asList(BOOKS_BY_MAIN_AUTHOR, singleton(new Triple(x, author, author1)),
                   emptyList(), emptyList()),
            asList(BOOKS_BY_MAIN_AUTHOR, singleton(new Triple(x, author, y)),
                   emptyList(), emptyList())
        ).map(List::toArray).collect(Collectors.toList());
        return Stream.concat(plain.stream(), semantic.stream()).toArray(Object[][]::new);
    }

    @Test(dataProvider = "semanticMatchData")
    public void testSemanticMatch(APIMolecule apiMolecule, Collection<Triple> query,
                                  Collection<CQuery> egs,
                                  List<? extends Set<CQuery>> alternatives) {
        TransitiveClosureTBoxReasoner reasoner = new TransitiveClosureTBoxReasoner();
        TBoxSpec tboxSpec = new TBoxSpec()
                .addResource(getClass(), "../../api-molecule-matcher-tests.ttl");
        reasoner.load(tboxSpec);

        APIMoleculeMatcher matcher = new APIMoleculeMatcher(apiMolecule, reasoner);
        SemanticCQueryMatch match = matcher.semanticMatch(CQuery.from(query));
        assertCompatible(match.getKnownExclusiveGroups(), egs);

        for (CQuery group : match.getKnownExclusiveGroups()) {
            for (CQuery alternative : match.getAlternatives(group)) {
                assertEquals(alternative.getMatchedTriples(), group.getSet());
            }
        }

        assertEquals(egs.size(), alternatives.size());
        Iterator<CQuery> egIt = egs.iterator();
        for (Collection<CQuery> expectedAlternatives : alternatives) {
            CQuery exclusiveGroup = egIt.next();
            for (CQuery candidate : match.getKnownExclusiveGroups()) {
                if (candidate.getSet().equals(exclusiveGroup.getSet()))
                    exclusiveGroup = candidate;
            }
            if (expectedAlternatives == null)
                expectedAlternatives = singleton(exclusiveGroup);
            Set<CQuery> actualAlternatives = match.getAlternatives(exclusiveGroup);
            assertCompatible(actualAlternatives, expectedAlternatives);
        }
    }


    private CQuery preservePureDescriptiveAnnotationTest(CQuery query, Triple annotated) {
        CQueryMatch match = new APIMoleculeMatcher(BOOKS_BY_AUTHOR).match(query);
        assertEquals(match.getNonExclusiveRelevant().size(), 0);
        assertEquals(match.getKnownExclusiveGroups().size(), 1);

        CQuery eg = match.getKnownExclusiveGroups().get(0);
        assertTrue(eg.getTripleAnnotations(annotated).contains(PureDescriptive.INSTANCE));
        return eg;
    }

    @Test
    public void testPreservePureDescriptiveAnnotationInFullMatch() {
        Triple hasTitle = new Triple(x, title, crime);
        CQuery query = CQuery.with(hasTitle,
                                   new Triple(x, author, y),
                                   new Triple(y, authorName, authorName1))
                             .annotate(hasTitle, PureDescriptive.INSTANCE).build();
        CQuery eg = preservePureDescriptiveAnnotationTest(query, new Triple(x, title, crime));
        assertEquals(eg.getSet(), query.getSet());
    }

    @Test
    public void testPreservePureDescriptiveAnnotationInPartialMatch() {
        Triple hasTitle = new Triple(x, title, crime);
        CQuery query = CQuery.with(hasTitle, new Triple(x, author, y),
                                             new Triple(x, cites, z),
                                             new Triple(y, authorName, authorName1))
                .annotate(hasTitle, PureDescriptive.INSTANCE).build();
        CQuery eg = preservePureDescriptiveAnnotationTest(query, new Triple(x, title, crime));
        assertEquals(eg.getSet(),
                     Sets.newHashSet(hasTitle, new Triple(x, author, y),
                                               new Triple(y, authorName, authorName1)));
    }

    @Test
    public void testSemanticMatchPreservesPureDescriptive() {
        TransitiveClosureTBoxReasoner reasoner = new TransitiveClosureTBoxReasoner();
        TBoxSpec tboxSpec = new TBoxSpec()
                .addResource(getClass(), "../../api-molecule-matcher-tests.ttl");
        reasoner.load(tboxSpec);

        APIMoleculeMatcher matcher = new APIMoleculeMatcher(BOOKS_BY_MAIN_AUTHOR, reasoner);
        Triple hasTitle = new Triple(x, title, crime);
        Triple hasAuthor = new Triple(x, author, y);
        Triple hasCited = new Triple(x, cites, z);
        Triple hasName = new Triple(y, authorName, authorName1);
        CQuery query = CQuery.with(hasTitle, hasAuthor, hasCited, hasName)
                             .annotate(hasTitle, PureDescriptive.INSTANCE).build();

        SemanticCQueryMatch match = matcher.semanticMatch(query);
        assertEquals(match.getKnownExclusiveGroups().size(), 1);
        assertEquals(match.getNonExclusiveRelevant().size(), 0);

        CQuery eg = match.getKnownExclusiveGroups().get(0);
        assertEquals(eg.getSet(), Sets.newHashSet(hasTitle, hasAuthor, hasName));
        assertTrue(eg.getTripleAnnotations(hasTitle).contains(PureDescriptive.INSTANCE));
        assertFalse(eg.getTripleAnnotations(hasAuthor).contains(PureDescriptive.INSTANCE));

        Set<CQuery> alternatives = match.getAlternatives(eg);
        assertEquals(alternatives.size(), 1);

        CQuery alt = alternatives.iterator().next();
        assertTrue(alt.getTripleAnnotations(hasTitle).contains(PureDescriptive.INSTANCE));
    }

}