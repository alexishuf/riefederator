package br.ufsc.lapesd.riefederator.rel.sql;

import br.ufsc.lapesd.riefederator.description.molecules.Molecule;
import br.ufsc.lapesd.riefederator.description.molecules.MoleculeMatcher;
import br.ufsc.lapesd.riefederator.federation.Federation;
import br.ufsc.lapesd.riefederator.federation.SimpleFederationModule;
import br.ufsc.lapesd.riefederator.federation.Source;
import br.ufsc.lapesd.riefederator.model.term.Var;
import br.ufsc.lapesd.riefederator.model.term.std.StdVar;
import br.ufsc.lapesd.riefederator.query.CQuery;
import br.ufsc.lapesd.riefederator.query.Cardinality;
import br.ufsc.lapesd.riefederator.query.endpoint.AbstractTPEndpoint;
import br.ufsc.lapesd.riefederator.query.endpoint.CQEndpoint;
import br.ufsc.lapesd.riefederator.query.endpoint.Capability;
import br.ufsc.lapesd.riefederator.query.endpoint.QueryExecutionException;
import br.ufsc.lapesd.riefederator.query.results.Results;
import br.ufsc.lapesd.riefederator.query.results.ResultsExecutor;
import br.ufsc.lapesd.riefederator.query.results.impl.HashDistinctResults;
import br.ufsc.lapesd.riefederator.query.results.impl.LimitResults;
import br.ufsc.lapesd.riefederator.query.results.impl.SequentialResultsExecutor;
import br.ufsc.lapesd.riefederator.reason.tbox.TransitiveClosureTBoxReasoner;
import br.ufsc.lapesd.riefederator.rel.common.AnnotationStatus;
import br.ufsc.lapesd.riefederator.rel.common.RelationalMoleculeMatcher;
import br.ufsc.lapesd.riefederator.rel.common.RelationalResults;
import br.ufsc.lapesd.riefederator.rel.mappings.RelationalMapping;
import com.google.inject.Guice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.*;
import java.util.Properties;

public class JDBCCQEndpoint extends AbstractTPEndpoint implements CQEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(JDBCCQEndpoint.class);
    private static final Var s = new StdVar("s"), p = new StdVar("p"), o = new StdVar("o");


    private @Nonnull final RelationalMapping mapping;
    private @Nonnull final SqlGenerator sqlGenerator;
    private @Nonnull final Molecule molecule;
    private @Nonnull final MoleculeMatcher moleculeMatcher;
    private @Nonnull final String name;
    private @Nonnull final ConnectionSupplier connectionSupplier;

    @FunctionalInterface
    public interface ConnectionSupplier {
        @Nonnull Connection connect() throws SQLException;
    }

    /* --- --- --- Constructor & Builder --- --- --- */

    public JDBCCQEndpoint(@Nonnull RelationalMapping mapping, @Nonnull String name,
                          @Nonnull ConnectionSupplier connectionSupplier) {
        this.mapping = mapping;
        this.sqlGenerator = new SqlGenerator(mapping).setExposeJoinVars(true);
        this.name = name;
        this.connectionSupplier = connectionSupplier;
        this.molecule = mapping.createMolecule();
        TransitiveClosureTBoxReasoner empty = new TransitiveClosureTBoxReasoner();
        this.moleculeMatcher = new RelationalMoleculeMatcher(this.molecule, empty);
    }

    public static class Builder {
        private final @Nonnull RelationalMapping mapping;

        public Builder(@Nonnull RelationalMapping mapping) {
            this.mapping = mapping;
        }

        public @Nonnull
        JDBCCQEndpoint connectingTo(@Nonnull String jdbcUrl) {
            return new JDBCCQEndpoint(mapping, jdbcUrl, () -> DriverManager.getConnection(jdbcUrl));
        }
        public @Nonnull
        JDBCCQEndpoint connectingTo(@Nonnull String jdbcUrl,
                                    @Nonnull String user, @Nonnull String password) {
            return new JDBCCQEndpoint(mapping, jdbcUrl,
                                    () -> DriverManager.getConnection(jdbcUrl, user, password));
        }
        public @Nonnull
        JDBCCQEndpoint connectingTo(@Nonnull String jdbcUrl,
                                    @Nonnull Properties properties) {
            return new JDBCCQEndpoint(mapping, jdbcUrl,
                                    () -> DriverManager.getConnection(jdbcUrl, properties));
        }
        public @Nonnull
        JDBCCQEndpoint connectingTo(@Nonnull String name,
                                    @Nonnull ConnectionSupplier connectionSupplier) {
            return new JDBCCQEndpoint(mapping, name, connectionSupplier);
        }
        public @Nonnull
        JDBCCQEndpoint connectingTo(@Nonnull ConnectionSupplier connectionSupplier) {
            return new JDBCCQEndpoint(mapping, connectionSupplier.toString(), connectionSupplier);
        }
    }

    public static @Nonnull Builder createFor(@Nonnull RelationalMapping mapping) {
        return new Builder(mapping);
    }

    /* --- --- --- Getters --- --- --- */

    public @Nonnull Molecule getMolecule() {
        return molecule;
    }
    public @Nonnull MoleculeMatcher getDefaultMatcher() {
        return moleculeMatcher;
    }
    public @Nonnull String getName() {
        return name;
    }
    public @Nonnull Source asSource() {
        return new Source(getDefaultMatcher(), this);
    }

    /* --- --- --- Internals --- --- --- */

    private @Nonnull Results runUnderFederation(@Nonnull CQuery query) {
            SimpleFederationModule m = new SimpleFederationModule() {
                @Override
                protected void configureResultsExecutor() {
                    bind(ResultsExecutor.class).toInstance(new SequentialResultsExecutor());
                }
            };
            try (Federation federation = Guice.createInjector(m).getInstance(Federation.class)) {
                federation.addSource(asSource());
                return federation.query(query);
            }
    }

    private class SqlResults extends RelationalResults {
        private final @Nonnull Statement stmt;
        private final @Nonnull ResultSet rs;

        public SqlResults(@Nonnull RelationalRewriting sql,
                          @Nonnull Statement stmt, @Nonnull ResultSet rs) {
            super(sql, mapping);
            this.stmt = stmt;
            this.rs = rs;
        }

        @Override
        protected boolean relationalAdvance() throws SQLException {
            return rs.next();
        }

        @Override
        protected void relationalClose() throws SQLException {
            SQLException exception = null;
            try {
                stmt.close();
            } catch (SQLException e) {
                exception = e;
            }
            try {
                rs.close();
            } catch (SQLException e) {
                if (exception == null) exception = e;
                else                   exception.addSuppressed(e);
            }
            if (exception != null)
                throw exception;
        }

        @Override
        protected @Nullable Object relationalGetValue(String relationalVar) throws SQLException {
            return rs.getObject(relationalVar);
        }
    }

    /* --- --- --- Interface implementation --- --- --- */

    @Override
    public @Nonnull Results query(@Nonnull CQuery query) {
        AnnotationStatus st = new AnnotationStatus(query);
        if (!st.isValid()) {
            if (st.isEmpty()) return runUnderFederation(query);
            else              st.checkNotPartiallyAnnotated(); //throws IllegalArgumentException
        }
        RelationalRewriting sql;
        try {
            sql = sqlGenerator.transform(query);
            logger.debug("{} Query:\n  {}\nSQL:\n  {}", this, query.toString().replace("\n", "\n  "),
                         sql.getRelationalQuery().replace("\n", "\n  "));
        } catch (RuntimeException e) {
            throw new QueryExecutionException("Could not generate SQL for "+query, e);
        }
        try {
            Statement stmt = connectionSupplier.connect().createStatement();
            ResultSet rs = stmt.executeQuery(sql.getRelationalQuery());
            Results results = new SqlResults(sql, stmt, rs);
            // SqlResults implements FILTER()s and projection.
            // Maybe the SQL engine provided DISTINCT and LIMIT. If not (and required) provide here
            if (!sql.isDistinct())
                results = HashDistinctResults.applyIf(results, query);
            // LIMIT must always be re-enforced since the mapping may "unfold" the SQL results
            results = LimitResults.applyIf(results, query);
            return results;
        } catch (SQLException e) {
            throw new QueryExecutionException("Problem executing rewritten SQL query", e);
        }
    }

    @Override
    public @Nonnull Cardinality estimate(@Nonnull CQuery query, int estimatePolicy) {
        return Cardinality.UNSUPPORTED;
    }

    @Override
    public boolean hasRemoteCapability(@Nonnull Capability capability) {
        switch (capability) {
            case PROJECTION:
            case LIMIT:
            case DISTINCT:
            case SPARQL_FILTER:
                return true;
            default:
                // TODO add support for VALUES using IN
                return false;
        }
    }

    /* --- --- --- Object methods --- --- --- */
    @Override
    public String toString() {
        return "JDBCEndpoint("+name+")";
    }
}