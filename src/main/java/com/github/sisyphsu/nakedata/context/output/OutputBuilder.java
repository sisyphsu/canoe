package com.github.sisyphsu.nakedata.context.output;

import com.github.sisyphsu.nakedata.context.model.Frame;
import com.github.sisyphsu.nakedata.context.model.FrameMeta;
import com.github.sisyphsu.nakedata.io.OutputWriter;
import com.github.sisyphsu.nakedata.node.Node;
import com.github.sisyphsu.nakedata.node.array.ArrayNode;
import com.github.sisyphsu.nakedata.node.array.MixArrayNode;
import com.github.sisyphsu.nakedata.node.std.*;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 输出构造器，负责扫描源数据，序列化输出报文
 *
 * @author sulin
 * @since 2019-05-01 14:50:15
 */
public final class OutputBuilder {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z_$][\\w$]{0,63}$");

    private long version;

    private final boolean      enableCxt;
    private final OutputData   data;
    private final OutputSchema meta;
    private final FrameMeta    frameMeta;

    public OutputBuilder(boolean enableCxt) {
        this.version = 0L;
        this.enableCxt = enableCxt;
        this.data = new OutputData(enableCxt);
        this.meta = new OutputSchema(enableCxt);

        this.frameMeta = new FrameMeta();
    }

    public Frame buildOutput(Node node) {
        if (node == null) {
            throw new NullPointerException("node can't be null");
        }
        // step1. 构建FrameMeta
        FrameMeta meta = this.scan(node);

        // step2. 构建OutputFrame
        // TODO 输出数据

        return null;
    }

    /**
     * 输出报文的body区
     */
    public void writeNode(Node node, OutputWriter writer) {
        if (node.isNull()) {
            writer.writeVarInt(OutputData.ID_NULL);
            return;
        }
        int dataId;
        switch (node.dataType()) {
            case BOOL:
                dataId = ((BooleanNode) node).value() ? OutputData.ID_TRUE : OutputData.ID_FALSE;
                writer.writeVarInt(dataId);
                break;
            case FLOAT:
                dataId = data.findFloatID(((FloatNode) node).getValue());
                writer.writeVarInt(dataId);
                break;
            case DOUBLE:
                dataId = data.findDoubleID(((DoubleNode) node).getValue());
                writer.writeVarInt(dataId);
                break;
            case VARINT:
                dataId = data.findVarintID(((VarintNode) node).getValue());
                writer.writeVarInt(dataId);
                break;
            case STRING:
                dataId = data.findStringID(((StringNode) node).getValue());
                writer.writeVarInt(dataId);
                break;
            case SYMBOL:
                dataId = data.findSymbolID(((SymbolNode) node).getData());
                writer.writeVarInt(dataId);
                break;
            case ARRAY:
                this.writeArrayNode((ArrayNode) node, writer);
                break;
            case OBJECT:
                this.writeObjectNode((ObjectNode) node, writer);
                break;
        }
    }

    /**
     * 输出ArrayNode
     */
    void writeArrayNode(ArrayNode node, OutputWriter writer) {
        List<ArrayNode> arrayNodes;
        if (node instanceof MixArrayNode) {
            arrayNodes = node.getItems();
        } else {
            arrayNodes = Collections.singletonList(node);
        }
        for (int i = 0; i < arrayNodes.size(); i++) {
            // 1bit：表示Slice是否还有后续，即Array是否完整
            // 3bit：表示Slice的数据类型，如null、bool、byte、short、int、long、double、float等原生类型，这些array需要特殊处理
            // Xbit：如果是struct类型，则需要额外描述结构ID
            // 剩余：表示Slice的真实长度
            ArrayNode arrayNode = arrayNodes.get(i);
            writer.writeVarInt(0);
            switch (arrayNode.elementType()) {
                case BOOL:
                    BitSet bs = new BitSet(arrayNode.size());
                    for (int j = 0; j < arrayNode.getItems().size(); j++) {
                        bs.set(j, (Boolean) arrayNode.getItems().get(j));
                    }
                    for (byte b : bs.toByteArray()) {
                        writer.writeByte(b);
                    }
                    break;
                case FLOAT:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeFloat((Float) item);
                    }
                    break;
                case DOUBLE:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeDouble((Double) item);
                    }
                    break;
                case BYTE:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeByte((Byte) item);
                    }
                    break;
                case SHORT:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeShort((Short) item);
                    }
                    break;
                case INT:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeVarInt((Integer) item);
                    }
                    break;
                case LONG:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeVarInt((Long) item);
                    }
                    break;
                case STRING:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeVarInt(data.findStringID((String) item));
                    }
                    break;
                case SYMBOL:
                    for (Object item : arrayNode.getItems()) {
                        writer.writeVarInt(data.findSymbolID((String) item));
                    }
                    break;
                case OBJECT:
                    break;
            }
        }
    }

    /**
     * 输出ObjectNode，按照fields固定顺序，依次输出。
     */
    void writeObjectNode(ObjectNode node, OutputWriter writer) {
        String[] fields = node.getFields().keySet().toArray(new String[0]);
        // 输出structId
        if (enableCxt && node.isStable()) {
            writer.writeVarInt(meta.findCxtStructID(fields));
        } else {
            writer.writeVarInt(meta.findTmpStructID(fields));
        }
        // 输出fields，此处不care各个字段的数据类型
        for (String field : fields) {
            Node subNode = node.getFields().get(field);
            this.writeNode(subNode, writer);
        }
    }

    /**
     * 扫描元数据. 数据序列化之前扫描收集"变量名"的增量变化, 用于预处理NamePool以及甄别map与object。
     */
    FrameMeta scan(Node node) {
        data.clear();
        if (enableCxt) {
            meta.clear();
        }
        // 执行扫描
        this.doScan(node);

        this.frameMeta.setVersion((int) ((this.version++) % Integer.MAX_VALUE));
        return frameMeta;
    }

    /**
     * 扫描元数据
     */
    private void doScan(Node node) {
        if (node.isNull()) {
            return;
        }
        switch (node.dataType()) {
            case FLOAT:
                data.floatArea.add(((FloatNode) node).getValue());
                break;
            case DOUBLE:
                data.doubleArea.add(((DoubleNode) node).getValue());
                break;
            case VARINT:
                data.varintArea.add(((VarintNode) node).getValue());
                break;
            case STRING:
                data.stringArea.add(((StringNode) node).getValue());
                break;
            case SYMBOL:
                String symbol = ((SymbolNode) node).getData();
                if (enableCxt) {
                    data.symbolArea.add(symbol);
                } else {
                    data.stringArea.add(symbol);
                }
                break;
            case ARRAY:
                this.doScanArrayNode((ArrayNode) node);
                break;
            case OBJECT:
                this.doScanObjectNode((ObjectNode) node);
                break;
        }
    }

    /**
     * 扫描数组节点
     */
    private void doScanArrayNode(ArrayNode array) {
        if (array instanceof MixArrayNode) {
            for (Object item : array.getItems()) {
                this.doScanArrayNode((ArrayNode) item);
            }
            return;
        }
        // 数组成员是string、symbol、object则需要更新元数据
        switch (array.elementType()) {
            case STRING:
                for (Object item : array.getItems()) {
                    data.stringArea.add((String) item);
                }
                break;
            case SYMBOL:
                for (Object item : array.getItems()) {
                    if (enableCxt) {
                        data.symbolArea.add(String.valueOf(item));
                    } else {
                        data.stringArea.add(String.valueOf(item));
                    }
                }
                break;
            case OBJECT:
                for (Object item : array.getItems()) {
                    this.doScanObjectNode((ObjectNode) item);
                }
                break;
        }
    }

    /**
     * 扫描并整理Object节点的元数据
     */
    private void doScanObjectNode(ObjectNode node) {
        String[] fields = node.getFields().keySet().toArray(new String[0]);
        // 注册struct
        boolean isTmp = !enableCxt;
        for (int i = 0; i < fields.length && !isTmp; i++) {
            isTmp = NAME.matcher(fields[i]).matches();
        }
        if (isTmp) {
            meta.addTmpStruct(fields);
        } else {
            meta.addCxtStruct(fields);
        }
        // 扫描子节点
        for (Node subNode : node.getFields().values()) {
            this.doScan(subNode);
        }
    }

}