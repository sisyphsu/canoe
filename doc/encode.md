# Example 

user{
    id: 100
    name: "nick"
    tags: []
    settings: {
        hide: true
    }
}

# Step

1. match ObjectCodec#toMap -> map<Key, Value>
2. match Map#toNode; match Key#toString, Value#toNode

# 备注

BigDecimal、BigInteger等直接通过byte[]编码即可，底层协议不需要额外支持这些数据。