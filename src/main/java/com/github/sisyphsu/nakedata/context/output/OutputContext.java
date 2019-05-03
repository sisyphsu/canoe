package com.github.sisyphsu.nakedata.context.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.sisyphsu.nakedata.DataType;
import com.github.sisyphsu.nakedata.context.*;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 输出上下文
 *
 * @author sulin
 * @since 2019-05-01 14:50:15
 */
public class OutputContext {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z_$][\\w$]{0,63}$");

    private OutputNamePool namePool;
    private OutputStructPool structPool;
    private OutputTypePool typePool;
    private ContextLog log;

    public OutputContext() {
        this.namePool = new OutputNamePool(1 << 16);
        this.structPool = new OutputStructPool(1 << 16);
        this.typePool = new OutputTypePool(1 << 16);
        this.log = new ContextLog();
    }

    /**
     * 预扫描元数据. 数据序列化之前扫描收集"变量名"的增量变化, 用于预处理NamePool以及甄别map与object。
     *
     * @param node 原始数据
     * @return 返回上下文元数据增量版本数据
     */
    public ContextVersion preScan(JsonNode node) {
        if (node == null) {
            throw new IllegalStateException("node can't be null");
        }
        // 重置log
        this.log.reset();
        // 开始扫描
        this.doScan(node);
        // 执行垃圾回收
        this.namePool.tryRelease(log);
        this.structPool.tryRelease(log);
        this.typePool.tryRelease(log);

        return null;
    }

    private int doScan(JsonNode node) {
        switch (node.getNodeType()) {
            case NULL:
                return DataType.NULL;
            case BOOLEAN:
                return node.booleanValue() ? DataType.TRUE : DataType.FALSE;
            case STRING:
                return DataType.STRING;
            case BINARY:
                return DataType.BINARY;
            case NUMBER:
                if (node.isFloat()) {
                    return DataType.FLOAT;
                } else if (node.isDouble()) {
                    return DataType.DOUBLE;
                } else {
                    return DataType.NUMBER;
                }
            case ARRAY:
                ArrayNode arrayNode = (ArrayNode) node;
                for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
                    this.preScan(it.next());
                }
                return DataType.ARRAY;
            case OBJECT:
                ObjectNode objectNode = (ObjectNode) node;
                boolean isTmp = false;
                Map<String, Integer> fields = new TreeMap<>();
                for (Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    isTmp = isTmp || NAME.matcher(entry.getKey()).matches();
                    int type = this.doScan(entry.getValue());// 继续扫描子元素
                    fields.put(entry.getKey(), type);
                }
                if (isTmp) {
                    // 处理临时类型, TODO 应该放入TMP中
//                this.structPool.buildStruct()
//                this.tmpTypes.add(objectNode);
                } else {
                    // 处理上下文类型
                    ContextName[] names = new ContextName[fields.size()];
                    int[] types = new int[fields.size()];
                    int index = 0;
                    for (Map.Entry<String, Integer> entry : fields.entrySet()) {
                        names[index] = namePool.buildName(log, entry.getKey());
                        types[index] = entry.getValue();
                        index++;
                    }
                    ContextStruct struct = structPool.buildStruct(log, names);
                    ContextType type = typePool.buildType(log, struct, types);
                }
                return DataType.OBJECT;
            default:
                throw new IllegalArgumentException("Unsupport data: " + node.getNodeType());
        }
    }

    /**
     * 获取指定JsonNode的type-id
     *
     * @param node 原始JsonNode
     * @return 类型ID
     */
    public int getTypeId(JsonNode node) {
        return 0;
    }

//    /**
//     * 根据待序列化数据刷新上下文, 主要更新元数据
//     *
//     * @param data 待序列化的数据
//     */
//    public void flush(JsonNode data) {
//        // null是默认数据类型, 不需要处理
//        if (data == null) {
//            return;
//        }
//        this.doCollect(data);
//    }

//    private TypeRef doCollect(JsonNode node) {
//        if (node == null) {
//            return null; // NULL
//        }
//        switch (node.getNodeType()) {
//            case NULL:
//                return null; // null
//            case BOOLEAN:
//                return null; // boolean
//            case NUMBER:
//                return null; // varint or double
//            case BINARY:
//                return null; // binary
//            case STRING:
//                return null; // string
//            case ARRAY:
//                ArrayNode arrayNode = (ArrayNode) node;
//                for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
//                    this.doCollect(it.next());
//                }
//                return null; // array
//            case OBJECT:
//                ObjectNode objectNode = (ObjectNode) node;
//                TypeRef ref = new TypeRef();
//                for (Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext(); ) {
//                    Map.Entry<String, JsonNode> entry = it.next();
//                    String name = entry.getKey();
//                    JsonNode val = entry.getValue();
//                    TypeRef type = this.doCollect(val);
//                    // collect name
//                    cxtNames.add(name);
//                    // collect fields
//                    ref.getFields().add(new TypeRef.Field(name, 0));
//                }
////                types.add(ref);
//
//                return ref; // object
//            default:
//                throw new IllegalStateException("unsupport data: " + node.getNodeType());
//        }
//    }

}
