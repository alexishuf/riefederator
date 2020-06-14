package br.ufsc.lapesd.riefederator.jena;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static br.ufsc.lapesd.riefederator.jena.ModelUtils.list;
import static org.apache.jena.riot.RDFLanguages.filenameToLang;

/**
 * Builder-style class to load ontologies and their imports
 */
public class TBoxLoader {
    private boolean fetchImports = true;
    private final @Nonnull Set<Resource> fetched = new HashSet<>();
    private final @Nonnull Map<String, String> uri2resource = new HashMap<>();
    private @Nullable Model model;
    private final @Nullable String name;

    private static final @Nonnull Logger logger = LoggerFactory.getLogger(TBoxLoader.class);
    private static final @Nonnull ConcurrentHashMap<String, String> globalUri2Resource =
            new ConcurrentHashMap<>();
    private static final @Nonnull Pattern END_URI_RX = Pattern.compile("[/#]*$");

    static {
        String dir = "br/ufsc/lapesd/riefederator";
        addToGlobalCache("http://www.w3.org/1999/02/22-rdf-syntax-ns", dir+"/rdf.ttl");
        addToGlobalCache("http://www.w3.org/2000/01/rdf-schema", dir+"/rdf-schema.ttl");
        addToGlobalCache("http://www.w3.org/2002/07/owl", dir+"/owl.ttl");
        addToGlobalCache("http://xmlns.com/foaf/0.1/", dir+"/foaf.rdf");
        addToGlobalCache("http://www.w3.org/2006/time", dir+"/time.ttl");
        addToGlobalCache("http://www.w3.org/ns/prov", dir+"/prov-o.ttl");
        addToGlobalCache("http://www.w3.org/ns/prov-o", dir+"/prov-o.ttl");
        addToGlobalCache("http://www.w3.org/2004/02/skos/core", dir+"/skos.rdf");
        addToGlobalCache("http://purl.org/dc/elements/1.1/", dir+"/dcelements.ttl");
        addToGlobalCache("http://purl.org/dc/dcam/", dir+"/dcam.ttl");
        addToGlobalCache("http://purl.org/dc/dcmitype/", dir+"/dctype.ttl");
        addToGlobalCache("http://purl.org/dc/terms/", dir+"/dcterms.ttl");
    }

    /* --- configurations --- */

    public boolean getFetchImports() {
        return fetchImports;
    }
    public TBoxLoader fetchingImports(boolean fetchImports) {
        this.fetchImports = fetchImports;
        return this;
    }

    private static void addToCache(@Nonnull String uri, @Nonnull String absoluteResourcePath,
                                   @Nonnull Map<String, String> map) {
        if (absoluteResourcePath.startsWith("/"))
            absoluteResourcePath = absoluteResourcePath.substring(1);
        map.put(END_URI_RX.matcher(uri).replaceAll(""), absoluteResourcePath);
    }

    private static void addToCache(@Nonnull String uri, @Nonnull Class<?> cls,
                                   @Nonnull String relativePath,
                                   @Nonnull Map<String, String> map) {
        String absPath = cls.getPackage().getName().replace('.', '/')
                       + "/" + relativePath;
        addToCache(uri, absPath, map);
    }

    public static void addToGlobalCache(@Nonnull String uri, @Nonnull String absoluteResourcePath) {
        addToCache(uri, absoluteResourcePath, globalUri2Resource);
    }

    public static void addToGlobalCache(@Nonnull String uri, @Nonnull Class<?> cls,
                                  @Nonnull String relativePath) {
        addToCache(uri, cls, relativePath, globalUri2Resource);
    }

    /* --- general --- */

    public TBoxLoader(@Nullable String name) {
        this.name = name;
    }

    public TBoxLoader() {
        this(null);
    }

    @Override
    public @Nonnull String toString() {
        return name != null ? "["+name+"]" : super.toString();
    }

    public @Nonnull Model getModel() {
        if (model == null)
            model = ModelFactory.createDefaultModel();
        return model;
    }


    /* --- load stuff --- */

    @Contract("_ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader setModel(@Nonnull Model model) {
        this.model = model;
        if (fetchImports) {
            List<Resource> os = list(model, null, OWL2.imports, null, Statement::getResource)
                    .filter(r -> !fetched.contains(r))
                    .collect(Collectors.toList());
            os.forEach(this::fetchOntology);
        }
        return this;
    }

    /**
     * Copies the contents of model into the TBox. Ownership of model is not transferred
     */
    @Contract("_ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addModel(@Nonnull Model model) {
        list(model, null, RDF.type, OWL2.Ontology, Statement::getSubject).forEach(fetched::add);
        this.getModel().add(model);
        if (fetchImports) {
            list(model, null, OWL2.imports, null, Statement::getResource)
                    .filter(r -> !fetched.contains(r))
                    .forEach(this::fetchOntology);
        }
        return this;
    }

    @Contract("_ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addFile(@Nonnull File file) throws IOException {
        String base = "file://" + file.getAbsolutePath();
        Model model = ModelFactory.createDefaultModel();
        try (FileInputStream in = new FileInputStream(file)) {
            RDFDataMgr.read(model, in, base, filenameToLang(file.getName(), Lang.TTL));
        }
        return addModel(model);
    }

    @Contract("_, _ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addInputStream(@Nonnull InputStream in, @Nonnull Lang lang) {
        String base = "inputstream://" + System.identityHashCode(in);
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, in, base, lang);
        return addModel(model);
    }

    @Contract("_, _ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader mapping(@Nonnull String uri, @Nonnull String absoluteResourcePath) {
        addToCache(uri, absoluteResourcePath, uri2resource);
        return this;
    }

    @Contract("_, _, _ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader mapping(@Nonnull String uri, @Nonnull Class<?> cls,
                                       @Nonnull String relativeResourcePath) {
        addToCache(uri, cls, relativeResourcePath, uri2resource);
        return this;
    }

    @CheckReturnValue
    private @Nullable Model loadFromResource(@Nonnull String path, @Nonnull ClassLoader loader,
                                             @Nonnull Lang lang) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) return null;
            RDFDataMgr.read(model, stream, lang);
            return model;
        } catch (IOException e) {
            throw new RuntimeException("IOException reading from resource " + path, e);
        }
    }

    /**
     * Loads RDF into the TBox from a resource file.
     *
     * @param path absolute or relative (to this class) path to the resource path
     * @param loader class loader to try first
     */
    @Contract("_, _ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addFromResource(@Nonnull String path,
                               @Nonnull ClassLoader loader) {
        Lang lang = RDFLanguages.filenameToLang(path);
        ClassLoader system = ClassLoader.getSystemClassLoader();
        String pathNoTrailing = path.startsWith("/") ? path.substring(1) : path;

        Model model = loadFromResource(path, loader, lang);
        if (model == null)
            model = loadFromResource(pathNoTrailing, system, lang);
        if (model == null && !path.startsWith("/"))
            model = loadFromResource("br/ufsc/lapesd/riefederator/" + path, system, lang);
        if (model == null && !path.startsWith("/"))
            model = loadFromResource("br/ufsc/lapesd/riefederator/" + path, loader, lang);
        if (model == null && !path.startsWith("/"))
            model = loadFromResource("/br/ufsc/lapesd/riefederator/" + path, loader, lang);
        if (model == null)
            throw new RuntimeException("Resource not found", new FileNotFoundException(path));
        else
            addModel(model);
        return this;
    }

    @Contract("_ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addFromResource(@Nonnull String path) {
        return addFromResource(path, getClass().getClassLoader());
    }

    @Contract("-> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addRDF() {
        addFromResource("rdf.ttl");
        return this;
    }

    @Contract("-> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addRDFS() {
        addFromResource("rdf-schema.ttl");
        return this;
    }

    @Contract("-> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader addOWL() {
        addFromResource("owl.ttl");
        return this;
    }

    @Contract("_ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader fetchOntology(@Nonnull Resource ontology) {
        if (ontology.isAnon()) return this;
        return  fetchOntology(ontology.getURI());
    }

    @Contract("_ -> this") @CanIgnoreReturnValue
    public @Nonnull TBoxLoader fetchOntology(@Nonnull String uri) {
        uri = END_URI_RX.matcher(uri).replaceAll("");
        Model model = ModelFactory.createDefaultModel();
        String path = uri2resource.get(uri);
        if (path == null)
            path = globalUri2Resource.get(uri);
        if (path != null) {
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in != null) {
                    RDFDataMgr.read(model, in, RDFLanguages.filenameToLang(path));
                    addModel(model);
                    return this; //done
                } else {
                    logger.warn("Resource {}, registered for URI {}, not found", path, uri);
                }
            } catch (IOException e) {
                logger.error("Problem while loading RDF from {} in place of URI {}", path, uri, e);
            }
        }
        // if we got here get from the URI
        RDFDataMgr.read(model, uri);
        addModel(model);
        return this;
    }
}