package com.google.collinsmith70.diablo.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class FixedArrayCache<V> implements Collection<V> {

private static final int DEFAULT_SIZE = 1<<8;

private Object[] data;
private int head;
private int size;

public FixedArrayCache() {
    this(DEFAULT_SIZE);
}

public FixedArrayCache(int size) {
    if (size < 0) {
        throw new IllegalArgumentException(
                "size must be a positive integer");
    }

    clear(size);
}

public int size() {
    return size;
}

public boolean isEmpty() {
    return size() != 0;
}

public void clear() {
    clear(data.length);
}

private void clear(int size) {
    assert size > 0 : "size should be > 0";
    this.data = new Object[size];
    this.head = 0;
    this.size = 0;
}

public void push(V obj) {
    data[head] = obj;
    head = increment(head);
    size = Math.min(data.length, size + 1);
}

@SuppressWarnings("unchecked")
protected V get(int i) {
    return (V)data[i];
}

private int increment(int i) {
    if (i + 1 >= data.length) {
        return 0;
    }

    return i + 1;
}

private int decrement(int i) {
    if (i - 1 < 0) {
        return data.length - 1;
    }

    return i - 1;
}

@Override
public Iterator<V> iterator() {
    return new Iterator<V>() {

        private int head = FixedArrayCache.this.decrement(
                FixedArrayCache.this.head);
        private int iterations = 0;

        @Override
        public boolean hasNext() {
            return iterations < FixedArrayCache.this.size()
                    && FixedArrayCache.this.get(head) != null;
        }

        @Override
        public V next() {
            V data = FixedArrayCache.this.get(head);
            head = FixedArrayCache.this.decrement(head);
            iterations++;
            return data;
        }

    };
}

@Override
public boolean contains(Object o) {
    throw new UnsupportedOperationException("Not supported.");
}

@Override
public Object[] toArray() {
    return Arrays.copyOf(data, data.length);
}

@Override
@SuppressWarnings("unchecked")
public <T> T[] toArray(T[] a) {
    // TODO: validate that a is a superclass of "V"
    return Arrays.copyOf((T[])data, data.length);
}

@Override
public boolean add(V v) {
    push(v);
    return true;
}

@Override
public boolean remove(Object o) {
    throw new UnsupportedOperationException("Not supported.");
}

@Override
public boolean containsAll(Collection<?> c) {
    throw new UnsupportedOperationException("Not supported.");
}

@Override
public boolean addAll(Collection<? extends V> c) {
    for (V obj : c) {
        add(obj);
    }

    return true;
}

@Override
public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("Not supported.");
}

@Override
public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("Not supported.");
}

}