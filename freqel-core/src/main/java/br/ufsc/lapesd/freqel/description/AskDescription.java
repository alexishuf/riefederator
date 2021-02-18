package br.ufsc.lapesd.freqel.description;

import br.ufsc.lapesd.freqel.model.Triple;
import br.ufsc.lapesd.freqel.model.term.Term;
import br.ufsc.lapesd.freqel.model.term.std.StdVar;
import br.ufsc.lapesd.freqel.query.CQuery;
import br.ufsc.lapesd.freqel.query.MutableCQuery;
import br.ufsc.lapesd.freqel.query.endpoint.Capability;
import br.ufsc.lapesd.freqel.query.endpoint.TPEndpoint;
import br.ufsc.lapesd.freqel.query.modifiers.Ask;
import br.ufsc.lapesd.freqel.query.results.Results;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static java.lang.Math.max;

public class AskDescription implements Description {
    private static final StdVar surrogateSubject = new StdVar("AskDescriptionSurrogateSubject");
    private static final StdVar surrogatePredicate  = new StdVar("AskDescriptionSurrogatePredicate");
    private static final StdVar surrogateObject  = new StdVar("AskDescriptionSurrogateObject");
    private static final @Nonnull Logger logger = LoggerFactory.getLogger(AskDescription.class);
    private static final int DEFAULT_CACHE_SIZE = 8192;
    private @Nonnull final TPEndpoint endpoint;
    private final LoadingCache<Triple, Boolean> cache;

    /* ~~~ Constructors ~~~ */

    public AskDescription(@Nonnull TPEndpoint endpoint, int cacheSize) {
        this.endpoint = endpoint;
        if (!endpoint.hasRemoteCapability(Capability.ASK))
            logger.warn("{} has no ASK capability. Will fetch first result instead", endpoint);
        this.cache = CacheBuilder.newBuilder().initialCapacity(max(256, cacheSize))
                .maximumSize(cacheSize)
                .build(new CacheLoader<Triple, Boolean>() {
                    @Override
                    public Boolean load(@Nonnull Triple triple) {
                        MutableCQuery query = MutableCQuery.from(triple);
                        query.mutateModifiers().add(Ask.INSTANCE);
                        try (Results results = endpoint.query(query)) {
                            return results.hasNext();
                        }
                    }
                });
    }

    public AskDescription(@Nonnull TPEndpoint endpoint) {
        this(endpoint, DEFAULT_CACHE_SIZE);
    }

    /* ~~~ private methods ~~~ */

    private @Nonnull Triple sanitize(@Nonnull Triple triple) {
        Term p = triple.getPredicate(), o = triple.getObject();
        if (p.isVar()) p = surrogatePredicate;
        if (o.isVar()) o = surrogateObject;
        return new Triple(surrogateSubject, p, o);
    }

    private boolean match(@Nonnull Triple t) {
        t = sanitize(t);
        boolean ok = false;
        if (t.getObject().isGround()) {
            Boolean maybe = cache.getIfPresent(t.withObject(surrogateObject));
            if (maybe != null && !maybe)
                return false; //generalized already failed, do not try new ASK
        }
        try {
            ok = cache.get(t);
            if (t.getObject().isGround()) {
                if (ok) {
                    // remember that the broadened query is also satisfied
                    cache.put(t.withObject(surrogateObject), true);
                } else {
                    // try to fail a broadened query to avoid further ASKs
                    cache.get(t.withObject(surrogateObject));
                }
            }
        } catch (ExecutionException | UncheckedExecutionException e) {
            logger.error("Exception while sending ASK.", e.getCause());
        }
        return ok;
    }

    /* ~~~ Public methods ~~~ */

    @Override
    public @Nonnull CQueryMatch match(@Nonnull CQuery query) {
        CQueryMatch.Builder b = null;
        for (Triple triple : query) {
            if (match(triple)) {
                (b == null ? b = CQueryMatch.builder(query) : b).addTriple(triple);
            }
        }
        return b == null ? CQueryMatch.EMPTY : b.build();
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
        return String.format("AskDescription(%s)", endpoint);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AskDescription)) return false;
        AskDescription that = (AskDescription) o;
        return endpoint.equals(that.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint);
    }
}