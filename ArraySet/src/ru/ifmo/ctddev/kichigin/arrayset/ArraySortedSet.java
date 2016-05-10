package ru.ifmo.ctddev.kichigin.arrayset;

import java.util.*;
import java.lang.ClassCastException;

/**
 * Created by Azarn on 29.02.2016.
 */

class ReversedOrderListAdapter<E> extends AbstractList<E> {
    private List<E> list;

    ReversedOrderListAdapter(List<E> list) {
        this.list = list;
    }

    @Override
    public E get(int index) {
        return list.get(size() - 1 - index);
    }

    @Override
    public int size() {
        return list.size();
    }
}

public class ArraySortedSet<E extends Comparable<? super E>> extends AbstractSet<E> implements NavigableSet<E> {
    private List<E> list;
    private Comparator<? super E> comp;

    public ArraySortedSet() {
        list = null;
        comp = null;
    }

    public ArraySortedSet(Collection<E> collection) {
        this(collection, null);
    }

    public ArraySortedSet(Collection<E> collection, Comparator<? super E> comparator) {
        if (comparator == null)
            comparator = Comparator.<E>naturalOrder();

        comp = comparator;
        list = new ArrayList<>();

        List<E> al = new ArrayList<>(collection);
        al.sort(comp);

        E last = null;
        for (E elem : al) {
            if (last == null || comp.compare(last, elem) != 0) {
                list.add(elem);
                last = elem;
            }
        }
    }

    @Override
    public boolean contains(Object elem) {
        return Collections.binarySearch(list, (E)elem, comp) >= 0;
    }

    @Override
    public int size() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Iterator<E> iterator() {
        return new ArraySetIterator();
    }

    @Override
    public Iterator<E> descendingIterator() {
        return new ArraySetReversedIterator();
    }

    @Override
    public Comparator<? super E> comparator() {
        return comp == Comparator.naturalOrder() ? null : comp;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                                  E toElement,   boolean toInclusive) {

        int from = afterIndex(fromElement, fromInclusive);
        int to = beforeIndex(toElement, toInclusive);

        if (from == -1 || from > to) {
            return createFromList(null, comp);
        }

        return createFromList(list.subList(from, to + 1), comp);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return subSet(isEmpty() ? toElement : first(), true, toElement, inclusive);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return subSet(fromElement, inclusive, isEmpty() ? fromElement : last(), true);
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return createFromList(new ReversedOrderListAdapter<E>(list), comp);
    }

    @Override
    public E first() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(0);
    }

    @Override
    public E last() {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(size() - 1);
    }

    @Override
    public E lower(E e) {
        int ind = beforeIndex(e, false);
        return ind == -1 ? null : list.get(ind);
    }

    @Override
    public E floor(E e) {
        int ind = beforeIndex(e, true);
        return ind == -1 ? null : list.get(ind);
    }

    @Override
    public E ceiling(E e) {
        int ind = afterIndex(e, true);
        return ind == -1 ? null : list.get(ind);
    }

    @Override
    public E higher(E e) {
        int ind = afterIndex(e, false);
        return ind == -1 ? null : list.get(ind);
    }

    @Override
    public E pollFirst() {
        return first();
    }

    @Override
    public E pollLast() {
        return last();
    }

    private ArraySortedSet<E> createFromList(List<E> newList, Comparator<? super E> comparator) {
        ArraySortedSet<E> ass = new ArraySortedSet<>();
        ass.list = newList;
        ass.comp = comparator;
        return ass;
    }

    private int afterIndex(E e, boolean inclusive) {
        int ind = Collections.binarySearch(list, e, comp);
        if (ind < 0) {
            ind = -ind - 1;
        } else if (!inclusive) {
            ++ind;
        }
        return ind >= size() ? -1 : ind;
    }

    private int beforeIndex(E e, boolean inclusive) {
        int ind = Collections.binarySearch(list, e, comp);
        if (ind < 0) {
            ind = -ind - 2;
        } else if (!inclusive) {
            --ind;
        }
        return ind < 0 ? -1 : ind;
    }


    final class ArraySetReversedIterator implements Iterator<E> {
        private int pos;

        ArraySetReversedIterator() {
            pos = size() - 1;
        }

        @Override
        public boolean hasNext() {
            return pos >= 0;
        }

        @Override
        public E next() {
            return list.get(pos--);
        }
    }

    final class ArraySetIterator implements Iterator<E> {
        private int pos;

        ArraySetIterator() {
            pos = 0;
        }

        @Override
        public boolean hasNext() {
            return pos < size();
        }

        @Override
        public E next() {
            return list.get(pos++);
        }
    }
}
