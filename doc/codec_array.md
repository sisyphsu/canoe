# Codec Roles for Array

## Encode to Node

`Array` should have three types:

+ `StdArrayNode`: ByteArray, BoolArray, FloatArray, DoubleArray, IntArray, LongArray... 
+ `ArrayNode`: ComplexArray, ObjectArray...

## Slice

Split all array into slices, make sure that every slice is totally clean.

Slice makes array have two level structure, it's a little complex.

## StdArray and ComArray 