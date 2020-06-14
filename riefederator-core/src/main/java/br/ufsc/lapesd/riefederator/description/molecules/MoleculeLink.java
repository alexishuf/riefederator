package br.ufsc.lapesd.riefederator.description.molecules;

import br.ufsc.lapesd.riefederator.model.term.Term;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.LazyInit;

import javax.annotation.Nonnull;
import java.util.Objects;

@Immutable
public class MoleculeLink {
    private final @Nonnull Term edge;
    private final @Nonnull Atom atom;
    private final boolean authoritative;
    private @LazyInit int hash = 0;

    public MoleculeLink(@Nonnull Term edge, @Nonnull Atom atom,
                        boolean authoritative) {
        this.edge = edge;
        this.atom = atom;
        this.authoritative = authoritative;
    }

    public @Nonnull Term getEdge() {
        return edge;
    }
    public @Nonnull Atom getAtom() {
        return atom;
    }

    /**
     * An authoritative link means that the set of instances found for this link in a source
     * are the universal set of existing links for the same subject.
     *
     * Not to be confused with {@link Atom}.isExclusive(). isExclusive() means that no other
     * source will have the the same subject. Thus isExclusive implies isAuthoritative for all
     * links, but the reverse does not hold in general.
     */
    public boolean isAuthoritative() {
        return authoritative;
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", getEdge(), getAtom());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MoleculeLink)) return false;
        MoleculeLink that = (MoleculeLink) o;
        return isAuthoritative() == that.isAuthoritative() &&
                getEdge().equals(that.getEdge()) &&
                getAtom().equals(that.getAtom());
    }

    @Override
    public int hashCode() {
        int local = hash;
        if (local == 0)
            hash = local = Objects.hash(getEdge(), getAtom(), isAuthoritative());
        return local;
    }
}