package com.github.sisyphsu.smartbuf.transport;

import com.github.sisyphsu.smartbuf.exception.InvalidDataException;
import com.github.sisyphsu.smartbuf.exception.UnexpectedReadException;

import java.io.IOException;

import static com.github.sisyphsu.smartbuf.transport.Const.*;

/**
 * InputContext holds the state of input's context, it helps decompress data and metadata's reusing.
 *
 * @author sulin
 * @since 2019-10-14 11:00:08
 */
final class InputDataPool {

    private final Array<Float>  floats  = new Array<>();
    private final Array<Double> doubles = new Array<>();
    private final Array<Long>   varints = new Array<>();
    private final Array<String> strings = new Array<>();

    private final IDAllocator   symbolID = new IDAllocator();
    private final Array<String> symbols  = new Array<>();

    /**
     * Execute synchronization for schema and metadata of context
     */
    public void read(InputBuffer buf) throws IOException {
        boolean hasMore = true;
        byte flag;
        while (hasMore) {
            long head = buf.readVarUint();
            int size = (int) (head >> 4);
            hasMore = (head & 0b0000_0001) == 1;
            flag = (byte) (head & 0b0000_1110);
            switch (flag) {
                case FLAG_DATA_FLOAT:
                    for (int i = 0; i < size; i++) {
                        floats.add(buf.readFloat());
                    }
                    break;
                case FLAG_DATA_DOUBLE:
                    for (int i = 0; i < size; i++) {
                        doubles.add(buf.readDouble());
                    }
                    break;
                case FLAG_DATA_VARINT:
                    for (int i = 0; i < size; i++) {
                        varints.add(buf.readVarInt());
                    }
                    break;
                case FLAG_DATA_STRING:
                    for (int i = 0; i < size; i++) {
                        strings.add(buf.readString());
                    }
                    break;
                case FLAG_DATA_SYMBOL_ADDED:
                    for (int i = 0; i < size; i++) {
                        String symbol = buf.readString();
                        int id = symbolID.acquire();
                        symbols.put(id, symbol);
                    }
                    break;
                case FLAG_DATA_SYMBOL_EXPIRED:
                    for (int i = 0; i < size; i++) {
                        int id = (int) buf.readVarUint();
                        symbolID.release(id);
                        symbols.put(id, null);
                    }
                    break;
                default:
                    throw new UnexpectedReadException("invalid flag: " + flag);
            }
        }
    }

    /**
     * Find a float data by its unique ID
     */
    public float getFloat(int index) throws InvalidDataException {
        if (index == 1) {
            return 0f;
        }
        try {
            return floats.get(index - 2);
        } catch (Exception e) {
            throw new InvalidDataException("invalid float id: " + index);
        }
    }

    /**
     * Find a double data by its unique ID
     */
    public double getDouble(int index) throws InvalidDataException {
        if (index == 1) {
            return 0.0;
        }
        try {
            return doubles.get(index - 2);
        } catch (Exception e) {
            throw new InvalidDataException("invalid double id: " + index);
        }
    }

    /**
     * Find a varint data by its unique ID
     */
    public long getVarint(int index) throws InvalidDataException {
        if (index == 1) {
            return 0L;
        }
        try {
            return varints.get(index - 2);
        } catch (Exception e) {
            throw new InvalidDataException("invalid varint id: " + index);
        }
    }

    /**
     * Find an string data by its unique ID
     */
    public String getString(int id) throws InvalidDataException {
        if (id == 1) {
            return "";
        }
        String str = null;
        try {
            str = strings.get(id - 2);
        } catch (Exception ignored) {
        }
        if (str == null) {
            throw new InvalidDataException("invalid string id: " + id);
        }
        return str;
    }

    /**
     * Find an symbol data by its unique ID
     */
    public String getSymbol(int id) throws InvalidDataException {
        int dataId = id - 1;
        if (dataId >= symbols.cap()) {
            throw new InvalidDataException("invalid symbol Id: " + id);
        }
        String symbol = symbols.get(dataId);
        if (symbol == null) {
            throw new InvalidDataException("invalid symbol id: " + id);
        }
        return symbol;
    }

    /**
     * reset this pool, but don't clean symbols
     */
    public void reset() {
        this.floats.clear();
        this.doubles.clear();
        this.varints.clear();
        this.strings.clear();
    }

}
