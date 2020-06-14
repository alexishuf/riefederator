package br.ufsc.lapesd.riefederator.federation.cardinality;

import br.ufsc.lapesd.riefederator.federation.cardinality.impl.ThresholdCardinalityComparator;
import br.ufsc.lapesd.riefederator.query.Cardinality;
import com.google.inject.ProvidedBy;

import javax.annotation.Nonnull;
import java.util.Comparator;

@ProvidedBy(ThresholdCardinalityComparator.SingletonProvider.class)
public interface CardinalityComparator extends Comparator<Cardinality> {

    default @Nonnull Cardinality min(@Nonnull Cardinality l, @Nonnull Cardinality r) {
        return compare(l, r) <= 0 ? l : r;
    }
    default @Nonnull Cardinality max(@Nonnull Cardinality l, @Nonnull Cardinality r) {
        return compare(l, r) >= 0 ? l : r;
    }
}