package br.ufsc.lapesd.freqel;

import br.ufsc.lapesd.freqel.deprecated.*;
import br.ufsc.lapesd.freqel.util.IdRefHashMap;
import br.ufsc.lapesd.freqel.util.RefHashMap;
import com.google.common.collect.Maps;
import org.openjdk.jmh.annotations.*;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class RefMapBenchmarks {

    @Param({"0", "4", "8", "16", "32", "128", "1024"})
    private int size;

    @Param({"id", "hash", "idhash", "hash+shift", "hash+simple", "hash+fastgrowth", "sorted", "sorted+Pair", "RefEquals+HashSet"})
    private String implementation;

    public static class Thing {
        private int id;

        public Thing(int id) {
            this.id = id;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Thing)) return false;
            Thing thing = (Thing) o;
            return id == thing.id;
        }

        @Override public int hashCode() {
            return Objects.hash(id);
        }
    }

    private Map<Thing, Integer> filledMap;
    private List<Thing> filledKeys;
    private Function<Integer, Map<Thing, Integer>> capacityFactory;
    private Supplier<Map<Thing, Integer>> factory;
    private Function<Object, Object> putAllFunction;

    @Setup(Level.Trial)
    public void setUp() {
        if (implementation.equals("id")) {
            capacityFactory = IdentityHashMap::new;
            factory = IdentityHashMap::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("idhash")) {
            capacityFactory = IdRefHashMap::new;
            factory = IdRefHashMap::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("hash")) {
            capacityFactory = RefHashMap::new;
            factory = RefHashMap::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("hash+shift")) {
            capacityFactory = RefHashMapShift::new;
            factory = RefHashMapShift::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("hash+simple")) {
            capacityFactory = RefHashMapSimpleGrowth::new;
            factory = RefHashMapSimpleGrowth::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("hash+fastgrowth")) {
            capacityFactory = RefHashMapFastGrowth::new;
            factory = RefHashMapFastGrowth::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("sorted")) {
            capacityFactory = RefSortedMap::new;
            factory = RefSortedMap::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("sorted+Pair")) {
            capacityFactory = RefSortedPairMap::new;
            factory = RefSortedPairMap::new;
            putAllFunction = this::putAllRefMap;
        } else if (implementation.equals("RefEquals+HashSet")) {
            capacityFactory = Maps::newHashMapWithExpectedSize;
            factory = HashMap::new;
            putAllFunction = this::putAllRefEquals;
        } else {
            throw new RuntimeException("Bad implementation="+implementation);
        }
        filledMap = capacityFactory.apply(size);
        filledKeys = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Thing key = new Thing(i);
            filledKeys.add(key);
            filledMap.put(key, i);
        }
    }

    private @Nonnull Map<Thing, Integer> putAllRefMap(@Nonnull Object in) {
        @SuppressWarnings("unchecked") Map<Thing, Integer> map = (Map<Thing, Integer>) in;
        for (int i = 0; i < size; i++)
            map.put(new Thing(i), i);
        return map;
    }

    private @Nonnull Map<RefEquals<Thing>, Integer> putAllRefEquals(@Nonnull Object in) {
        @SuppressWarnings("unchecked")
        Map<RefEquals<Thing>, Integer> map = (Map<RefEquals<Thing>, Integer>) in;
        for (int i = 0; i < size; i++)
            map.put(RefEquals.of(new Thing(i)), i);
        return map;
    }

    @Benchmark public @Nonnull Object putAllBenchmark() {
        return putAllFunction.apply(factory.get());
    }

    @Benchmark public @Nonnull Object putAllReservedBenchmark() {
        return putAllFunction.apply(capacityFactory.apply(size));
    }

    @Benchmark public int iteratorRemoveThenHashBenchmark() {
        Map<Thing, Integer> map = capacityFactory.apply(size);
        for (int i = 0; i < size; i++)
            map.put(new Thing(i), i);
        map.entrySet().removeIf(e -> e.getValue() % 4 == 0);
        return map.hashCode();
    }

    @Benchmark public int iterateBenchmark() {
        int sum = 0;
        for (Map.Entry<Thing, Integer> e : filledMap.entrySet())
            sum += e.getKey().id + e.getValue();
        return sum;
    }

    @Benchmark public int getAllBenchmark() {
        int sum = 0;
        for (int i = 0; i < size; i++)
            sum += filledMap.get(filledKeys.get(i));
        return sum;
    }

    @Benchmark public int getEvenBenchmark() {
        int sum = 0;
        for (int i = 0; i < size; i += 2)
            sum += filledMap.get(filledKeys.get(i));
        return sum;
    }
}
