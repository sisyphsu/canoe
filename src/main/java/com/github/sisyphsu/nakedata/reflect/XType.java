package com.github.sisyphsu.nakedata.reflect;

import java.util.Map;
import java.util.Objects;

/**
 * XType represent an clear Java type with raw class and its generic types.
 * <p>
 * There has stop-class concept, which will not be.
 *
 * @author sulin
 * @since 2019-07-15 20:40:47
 */
public class XType<T> {

    /**
     * Basic Object Type
     */
    private final Class<T> rawType;
    /**
     * Component type for Object[], support GenericArrayType
     */
    private XType<?> componentType;
    /**
     * ParameterizedType's name in decleared class, like [K, V] for Map<K, V>.
     */
    private String[] parameteriedNames;
    /**
     * Parsed types from ParameterizedType, like [String, Object] for HashMap<String, Object>
     */
    private XType<?>[] parameteriedTypes;
    /**
     * Fields, only for no-stop-class
     */
    private Map<String, XField> fields;

    public XType(Class<T> rawType) {
        this.rawType = rawType;
    }

    public XType(Class<T> rawType, XType<?> componentType) {
        this.rawType = rawType;
        this.componentType = componentType;
    }

    public XType(Class<T> rawType, String[] parameteriedNames, XType<?>[] parameteriedTypes) {
        this.rawType = rawType;
        this.parameteriedNames = parameteriedNames;
        this.parameteriedTypes = parameteriedTypes;
    }

    /**
     * Fetch the parameterized type for common generic type, like Collection's E type
     *
     * @return Generic Type
     */
    public XType<?> getParameterizedType() {
        if (this.parameteriedTypes == null || this.parameteriedTypes.length != 1) {
            throw new RuntimeException("Can't getParameterizedType from " + this.toString());
        }
        return this.parameteriedTypes[0];
    }

    /**
     * Fetch the specified parameterized type for named generic type.
     *
     * @param name Parameterized name
     * @return Parameterized type
     */
    public XType<?> getParameterizedType(String name) {
        if (this.parameteriedNames != null) {
            for (int i = 0; i < this.parameteriedNames.length; i++) {
                if (Objects.equals(this.parameteriedNames[i], name)) {
                    return this.parameteriedTypes[i];
                }
            }
        }
        return null;
    }

    /**
     * Fetch all parameterized type sort by place, like Map's K and V type
     *
     * @return Generic Types
     */
    public XType<?>[] getParameterizedTypes() {
        return this.parameteriedTypes;
    }

    /**
     * Check whether this XType is a pure class or not
     *
     * @return Pure type or not
     */
    public boolean isPure() {
        if (componentType != null) {
            return false;
        }
        return parameteriedNames == null;
    }

    public Class<T> getRawType() {
        return rawType;
    }

    protected void setFields(Map<String, XField> fields) {
        this.fields = fields;
    }

    public XField<?> getField(String name) {
        return this.fields.get(name);
    }

    public Map<String, XField> getFields() {
        return fields;
    }

    public XType<?> getComponentType() {
        return componentType;
    }

}