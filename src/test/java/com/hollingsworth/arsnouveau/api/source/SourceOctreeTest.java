package com.hollingsworth.arsnouveau.api.source;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Random;

/** Runnable without Minecraft: compares indexed queries against a linear reference. */
public final class SourceOctreeTest {
    public static void main(String[] args) {
        SourceOctree<Value> tree = new SourceOctree<>();
        IdentityHashMap<Value, int[]> expected = new IdentityHashMap<>();
        Value[] values = new Value[512];
        Random random = new Random(20261016);
        for (int i = 0; i < values.length; i++) values[i] = new Value(i);
        // Distinct objects compare equal; removing one must leave the other indexed.
        for (int i = 0; i < values.length; i++) {
            tree.put(values[i], 0, 0, 0);
            expected.put(values[i], new int[3]);
        }
        tree.remove(values[0]);
        expected.remove(values[0]);
        verify(tree, expected, 0, 0, 0, 0, false);
        for (int operation = 0; operation < 12000; operation++) {
            Value value = values[random.nextInt(values.length)];
            if (random.nextInt(4) == 0) {
                tree.remove(value);
                expected.remove(value);
            } else {
                int[] position = {coordinate(random), coordinate(random), coordinate(random)};
                tree.put(value, position[0], position[1], position[2]);
                expected.put(value, position);
            }
            if (tree.size() != expected.size()) throw new AssertionError("size mismatch");
            int[] center = expected.get(values[random.nextInt(values.length)]);
            if (center == null) center = new int[3];
            verify(
                    tree,
                    expected,
                    center[0],
                    center[1],
                    center[2],
                    operation % 50 == 0 ? Integer.MAX_VALUE : random.nextInt(65),
                    operation % 2 == 0);
        }
        for (Value value : values) tree.remove(value);
        expected.clear();
        verify(tree, expected, 0, 0, 0, Integer.MAX_VALUE, false);
        if (!tree.find(0, 0, 0, -1, value -> true).isEmpty())
            throw new AssertionError("negative range");
        System.out.println(
                "SourceOctree: 12000 randomized operations and identity/boundary checks passed");
    }

    private static int coordinate(Random random) {
        return switch (random.nextInt(12)) {
            case 0 -> Integer.MIN_VALUE;
            case 1 -> Integer.MAX_VALUE;
            case 2 -> 0;
            default -> random.nextInt(129) - 64;
        };
    }

    private static void verify(
            SourceOctree<Value> tree,
            IdentityHashMap<Value, int[]> expected,
            int x,
            int y,
            int z,
            int range,
            boolean evenOnly) {
        ArrayList<Value> result =
                tree.find(x, y, z, range, value -> !evenOnly || value.id % 2 == 0);
        IdentityHashMap<Value, Boolean> remaining = new IdentityHashMap<>();
        expected.forEach(
                (value, position) -> {
                    if ((!evenOnly || value.id % 2 == 0)
                            && Math.abs((long) position[0] - x) <= range
                            && Math.abs((long) position[1] - y) <= range
                            && Math.abs((long) position[2] - z) <= range)
                        remaining.put(value, true);
                });
        for (Value value : result) {
            if (remaining.remove(value) == null)
                throw new AssertionError("unexpected or duplicate result");
        }
        if (!remaining.isEmpty()) throw new AssertionError("missing result");
    }

    private static final class Value {
        private final int id;

        private Value(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Value;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
