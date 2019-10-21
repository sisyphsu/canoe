package com.github.sisyphsu.datube.convertor.codec;

import com.github.sisyphsu.datube.convertor.Codec;
import com.github.sisyphsu.datube.convertor.Converter;
import com.github.sisyphsu.datube.reflect.XType;

import java.util.*;
import java.util.concurrent.*;

/**
 * Collection's codec
 *
 * @author sulin
 * @since 2019-05-13 18:40:23
 */
@SuppressWarnings("unchecked")
public final class CollectionCodec extends Codec {

    /**
     * Convert Object[] to Collection
     */
    @Converter
    public Collection toCollection(Object[] arr) {
        return Arrays.asList(arr);
    }

    /**
     * Convert Collection to Iterable
     */
    @Converter
    public Iterable toIterable(Collection collection) {
        return collection;
    }

    /**
     * Converter Iterable to Collection
     */
    @Converter
    public Collection toCollection(Iterable it) {
        List<Object> list = new ArrayList<>();
        it.forEach(list::add);
        return list;
    }

    /**
     * Convert Collection to Iterator
     * Collection is compatible with Iterator, CollectionCodec will handle generic type.
     */
    @Converter
    public Iterator toIterator(Collection coll) {
        return coll.iterator();
    }

    /**
     * Convert Iterator to List as default collection
     */
    @Converter
    public Collection toCollection(Iterator it) {
        List<Object> list = new ArrayList<>();
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }

    /**
     * Convert Collection to Enumeration
     */
    @Converter
    public Enumeration toEnumeration(Collection<?> coll) {
        return Collections.enumeration(coll);
    }

    /**
     * Convert Enumeration to List
     */
    @Converter
    public Collection toCollection(Enumeration e) {
        List<Object> result = new ArrayList<>();
        while (e.hasMoreElements()) {
            result.add(e.nextElement());
        }
        return result;
    }

    /**
     * Convert Any Collection to Collection, support GenericType convert.
     */
    @Converter(extensible = true)
    public Collection toCollection(Collection src, XType<?> type) {
        // filter empty collection
        Class clz = type.getRawType();
        if (clz.isInstance(src) && src.isEmpty()) {
            return src;
        }
        // check compatible
        XType paramType = type.getParameterizedType();
        boolean compatible = clz.isInstance(src) && paramType.isPure();
        if (compatible && paramType.getRawType() != Object.class) {
            for (Object o : src) {
                if (o != null) {
                    compatible = paramType.getRawType().isAssignableFrom(o.getClass());
                }
                if (!compatible) {
                    break;
                }
            }
        }
        if (compatible) {
            return src;
        }
        // create target collection
        Collection result = create(clz, paramType.getRawType(), src.size());
        for (Object o : src) {
            result.add(convert(o, paramType));
        }
        return result;
    }

    /**
     * Create an collection instance by the specified Type.
     *
     * @param clz      Collection Type
     * @param itemType Element Type, for EnumSet
     * @param size     Initialize siz
     * @return Collection
     */
    @SuppressWarnings("unchecked")
    public static <T extends Collection> T create(Class<T> clz, Class itemType, int size) {
        Collection result;

        if (Set.class.isAssignableFrom(clz)) {
            if (clz.isAssignableFrom(HashSet.class)) {
                result = new HashSet();
            } else if (clz.isAssignableFrom(TreeSet.class)) {
                result = new TreeSet();
            } else if (clz.isAssignableFrom(LinkedHashSet.class)) {
                result = new LinkedHashSet();
            } else if (clz.isAssignableFrom(CopyOnWriteArraySet.class)) {
                result = new CopyOnWriteArraySet();
            } else if (clz.isAssignableFrom(ConcurrentSkipListSet.class)) {
                result = new ConcurrentSkipListSet();
            } else if (clz.isAssignableFrom(EnumSet.class)) {
                result = EnumSet.noneOf(itemType);
            } else {
                throw new UnsupportedOperationException("Unsupported Set: " + clz);
            }
        } else if (Queue.class.isAssignableFrom(clz)) {
            if (clz.isAssignableFrom(LinkedList.class)) {
                result = new LinkedList();
            } else if (clz.isAssignableFrom(ArrayDeque.class)) {
                result = new ArrayDeque(size);
            } else if (clz.isAssignableFrom(DelayQueue.class)) {
                result = new DelayQueue();
            } else if (clz.isAssignableFrom(LinkedBlockingDeque.class)) {
                result = new LinkedBlockingDeque(size);
            } else if (clz.isAssignableFrom(LinkedBlockingQueue.class)) {
                result = new LinkedBlockingQueue(size);
            } else if (clz.isAssignableFrom(LinkedTransferQueue.class)) {
                result = new LinkedTransferQueue();
            } else if (clz.isAssignableFrom(PriorityBlockingQueue.class)) {
                result = new PriorityBlockingQueue(size);
            } else if (clz.isAssignableFrom(ArrayBlockingQueue.class)) {
                result = new ArrayBlockingQueue(size);
            } else if (clz.isAssignableFrom(PriorityQueue.class)) {
                result = new PriorityQueue(size);
            } else if (clz.isAssignableFrom(SynchronousQueue.class)) {
                result = new SynchronousQueue();
            } else if (clz.isAssignableFrom(ConcurrentLinkedDeque.class)) {
                result = new ConcurrentLinkedDeque();
            } else if (clz.isAssignableFrom(ConcurrentLinkedQueue.class)) {
                result = new ConcurrentLinkedQueue();
            } else {
                throw new UnsupportedOperationException("Unsupported Queue: " + clz);
            }
        } else {
            if (clz.isAssignableFrom(ArrayList.class)) {
                result = new ArrayList();
            } else if (clz.isAssignableFrom(LinkedList.class)) {
                result = new LinkedList();
            } else if (clz.isAssignableFrom(Vector.class)) {
                result = new Vector();
            } else if (clz.isAssignableFrom(Stack.class)) {
                result = new Stack();
            } else if (clz.isAssignableFrom(CopyOnWriteArrayList.class)) {
                result = new CopyOnWriteArrayList();
            } else {
                throw new UnsupportedOperationException("Unsupported Collection: " + clz);
            }
        }
        return (T) result;
    }

}