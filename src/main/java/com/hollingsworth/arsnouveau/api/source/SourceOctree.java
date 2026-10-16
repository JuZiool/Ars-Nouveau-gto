package com.hollingsworth.arsnouveau.api.source;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.function.Predicate;

/** A server-thread spatial index covering the complete BlockPos coordinate range. */
public final class SourceOctree<T> {
    private static final int LEAF_CAPACITY = 16;
    private final IdentityHashMap<T, Entry<T>> entries = new IdentityHashMap<>();
    private final Node<T> root =
            new Node<>(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 1L << 32);

    public void put(T value, int x, int y, int z) {
        Entry<T> previous = entries.get(value);
        if (previous != null) {
            if (previous.x == x && previous.y == y && previous.z == z) return;
            root.remove(previous);
        }
        Entry<T> entry = new Entry<>(value, x, y, z);
        entries.put(value, entry);
        root.add(entry);
    }

    public void remove(T value) {
        Entry<T> entry = entries.remove(value);
        if (entry != null) root.remove(entry);
    }

    public int size() {
        return entries.size();
    }

    public ArrayList<T> find(int x, int y, int z, int range, Predicate<T> filter) {
        ArrayList<T> result = new ArrayList<>(Math.min(entries.size(), LEAF_CAPACITY));
        if (range >= 0) root.find(x, y, z, range, filter, result);
        return result;
    }

    private static final class Entry<T> {
        private final T value;
        private final int x;
        private final int y;
        private final int z;

        private Entry(T value, int x, int y, int z) {
            this.value = value;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Node<T> {
        private final long x;
        private final long y;
        private final long z;
        private final long size;
        private ArrayList<Entry<T>> values = new ArrayList<>(LEAF_CAPACITY);
        private Node<T>[] children;
        private int count;

        private Node(long x, long y, long z, long size) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
        }

        private int childIndex(Entry<T> entry) {
            long half = size >> 1;
            return (entry.x >= x + half ? 1 : 0)
                    | (entry.y >= y + half ? 2 : 0)
                    | (entry.z >= z + half ? 4 : 0);
        }

        private void addChild(Entry<T> entry) {
            int index = childIndex(entry);
            Node<T> child = children[index];
            if (child == null) {
                long half = size >> 1;
                children[index] =
                        child =
                                new Node<>(
                                        x + ((index & 1) != 0 ? half : 0),
                                        y + ((index & 2) != 0 ? half : 0),
                                        z + ((index & 4) != 0 ? half : 0),
                                        half);
            }
            child.add(entry);
        }

        @SuppressWarnings("unchecked")
        private void add(Entry<T> entry) {
            count++;
            if (children != null) {
                addChild(entry);
            } else if (values.size() < LEAF_CAPACITY || size == 1) {
                values.add(entry);
            } else {
                children = (Node<T>[]) new Node<?>[8];
                for (int i = 0; i < values.size(); i++) addChild(values.get(i));
                values = null;
                addChild(entry);
            }
        }

        private void remove(Entry<T> entry) {
            count--;
            if (children == null) {
                values.remove(entry);
            } else {
                int index = childIndex(entry);
                Node<T> child = children[index];
                child.remove(entry);
                if (child.count == 0) children[index] = null;
                if (count == 0) {
                    children = null;
                    values = new ArrayList<>(LEAF_CAPACITY);
                }
            }
        }

        private void find(
                int centerX,
                int centerY,
                int centerZ,
                int range,
                Predicate<T> filter,
                ArrayList<T> result) {
            if (count == 0
                    || x > (long) centerX + range
                    || x + size <= (long) centerX - range
                    || y > (long) centerY + range
                    || y + size <= (long) centerY - range
                    || z > (long) centerZ + range
                    || z + size <= (long) centerZ - range) return;
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    Node<T> child = children[i];
                    if (child != null) child.find(centerX, centerY, centerZ, range, filter, result);
                }
            } else {
                for (int i = 0; i < values.size(); i++) {
                    Entry<T> entry = values.get(i);
                    if (Math.abs((long) entry.x - centerX) <= range
                            && Math.abs((long) entry.y - centerY) <= range
                            && Math.abs((long) entry.z - centerZ) <= range
                            && filter.test(entry.value)) result.add(entry.value);
                }
            }
        }
    }
}
