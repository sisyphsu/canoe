package com.github.sisyphsu.datube.convertor;

import com.github.sisyphsu.datube.reflect.XType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CodecFactory, meantains CodecMethod and Pipeline.
 * It should scan all Codec to generate ConverterMethod, and cache all related classes to build ConverterMap,
 * Then use the ConverterMap to find the shortest data-converter-path.
 *
 * @author sulin
 * @since 2019-05-20 16:14:54
 */
@Slf4j
public final class CodecFactory {

    public static final CodecFactory Instance = new CodecFactory();

    private final Set<Codec>                   codecs       = ConcurrentHashMap.newKeySet();
    private final ConverterMap                 converterMap = new ConverterMap();
    private final Map<PKey, ConverterPipeline> pipelineMap  = new ConcurrentHashMap<>();

    /**
     * Initialize CodecFactory with the specified Codec type.
     */
    public CodecFactory() {
        for (Class<? extends Codec> codecCls : CodecScanner.scanCodecs()) {
            this.installCodec(codecCls);
        }
    }

    /**
     * Install new codec
     *
     * @param codecClass new codec's class
     */
    public void installCodec(Class<? extends Codec> codecClass) {
        try {
            Codec codec = codecClass.newInstance();
            this.installCodec(codec);
        } catch (Exception e) {
            log.warn("Ignore invalid codec, newInstance failed: " + codecClass);
        }
    }

    /**
     * Install new codec
     *
     * @param codec new codec
     */
    public void installCodec(Codec codec) {
        this.installCodec(Collections.singleton(codec));
    }

    /**
     * install new codec into this CodecFactory
     *
     * @param codecs new codec
     */
    public void installCodec(Set<Codec> codecs) {
        if (codecs == null) {
            return;
        }
        for (Codec codec : codecs) {
            if (codec == null || !this.codecs.add(codec)) {
                continue;
            }
            // collect all encode and decode methods
            for (Method method : codec.getClass().getDeclaredMethods()) {
                RealConverterMethod convertMethod = RealConverterMethod.valueOf(codec, method);
                if (convertMethod == null) {
                    continue;
                }
                converterMap.put(convertMethod);
            }
            codec.setFactory(this);
        }
        // reset all pipeline
        this.pipelineMap.clear();
    }

    /**
     * Execute data convert, convert src to the specified type target
     *
     * @param srcObj  Source Object
     * @param tgtType Target Type
     * @return Target Instance
     */
    public Object doConvert(Object srcObj, XType tgtType) {
        if (srcObj == null) {
            return null;
        }
        Class srcClass = srcObj.getClass();
        Class tgtClass = tgtType.getRawType();
        ConverterPipeline pipeline = this.getPipeline(srcClass, tgtClass);
        if (pipeline == null) {
            throw new IllegalStateException("Can't convert " + srcObj.getClass() + " to " + tgtType);
        }
        return pipeline.convert(srcObj, tgtType);
    }

    /**
     * Get the specified ConverterPipeline from srcClass to tgtClass
     *
     * @param srcClass Source class
     * @param tgtClass Target class
     * @return ConverterPipeline, could be null
     */
    public ConverterPipeline getPipeline(Class srcClass, Class tgtClass) {
        ConverterPipeline pipeline = pipelineMap.get(new PKey(srcClass, tgtClass));
        if (pipeline == null) {
            converterMap.flushCastConverter(srcClass);
            converterMap.flushCastConverter(tgtClass);
            // find the shortest path
            Path shortestPath = this.findShortestPath(srcClass, tgtClass);
            if (shortestPath != null) {
                pipeline = new ConverterPipeline(srcClass, tgtClass, shortestPath.methods);
                pipelineMap.put(new PKey(srcClass, tgtClass), pipeline);
            }
        }
        return pipeline;
    }

    /**
     * Find the shortest path from srcClass to tgtClass
     *
     * @param srcClass Source Class
     * @param tgtClass Target Class
     * @return The shortest path, could be null
     */
    public Path findShortestPath(Class<?> srcClass, Class<?> tgtClass) {
        return this.findShortestPath(null, srcClass, tgtClass);
    }

    /**
     * Find the shortest path from srcClass to tgtClass
     * Don't need care about inherit, because all subclass->class will be represent by TranConvertMethod
     *
     * @param passed Passed router which shouldn't be used again
     */
    private Path findShortestPath(Map<Class, Integer> passed, Class<?> srcClass, Class<?> tgtClass) {
        if (srcClass == tgtClass) {
            ConverterMethod method = converterMap.get(srcClass, tgtClass);
            return new Path(0, method);
        }
        passed = passed == null ? new HashMap<>() : new HashMap<>(passed);
        passed.compute(srcClass, (clz, v) -> 1 + (v == null ? 0 : v));
        // calculate all path
        List<Path> paths = new ArrayList<>();
        for (ConverterMethod route : converterMap.get(srcClass)) {
            int passTimes = passed.getOrDefault(route.getTgtClass(), 0);
            if (route.getSrcClass() != route.getTgtClass() && passTimes >= 1) {
                continue; // for normal node, allow pass once
            }
            if (route.getSrcClass() == route.getTgtClass() && passTimes >= 2) {
                continue; // for self-converted node, allow repass once
            }
            Path path;
            if (route.isExtensible() && route.getTgtClass().isAssignableFrom(tgtClass)) {
                path = this.findShortestPath(passed, tgtClass, tgtClass);
            } else {
                path = this.findShortestPath(passed, route.getTgtClass(), tgtClass);
            }
            if (path != null) {
                paths.add(new Path(route, path));
            }
        }
        // find shortest path if exists
        if (!paths.isEmpty()) {
            paths.sort(Comparator.comparingInt(o -> o.distance));
        }
        return paths.isEmpty() ? null : paths.get(0);
    }

    public ConverterMap getConverterMap() {
        return converterMap;
    }

    /**
     * Converter's Path
     */
    private static class Path {
        private final int                   distance;
        private final List<ConverterMethod> methods = new ArrayList<>();

        public Path(int distance, ConverterMethod method) {
            if (method != null) {
                this.methods.add(method);
            }
            this.distance = distance + (method == null ? 0 : method.getDistance());
        }

        public Path(ConverterMethod route, Path next) {
            this.distance = route.getDistance() + next.distance;
            this.methods.add(route);
            this.methods.addAll(next.methods);
        }
    }

    /**
     * Pipeline's key, used for Map
     */
    @Data
    @AllArgsConstructor
    private static class PKey {
        private final Class<?> srcClass;
        private final Class<?> tgtClass;
    }

}