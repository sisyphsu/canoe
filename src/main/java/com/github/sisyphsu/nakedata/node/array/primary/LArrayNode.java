package com.github.sisyphsu.nakedata.node.array.primary;

import com.github.sisyphsu.nakedata.node.Node;
import com.github.sisyphsu.nakedata.type.DataType;

/**
 * long[] array
 *
 * @author sulin
 * @since 2019-06-05 15:54:35
 */
public class LArrayNode extends Node {

    private long[] items;

    private LArrayNode(long[] items) {
        this.items = items;
    }

    public static LArrayNode valueOf(long[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data can't be null or empty");
        }
        return new LArrayNode(data);
    }

    public long[] getItems() {
        return items;
    }

    @Override
    public DataType dataType() {
        return DataType.LONG;
    }

    @Override
    public boolean isNull() {
        return false;
    }

}
