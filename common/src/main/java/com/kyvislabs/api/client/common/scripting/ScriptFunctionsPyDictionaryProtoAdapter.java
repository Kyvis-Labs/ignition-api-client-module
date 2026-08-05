package com.kyvislabs.api.client.common.scripting;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.inductiveautomation.ignition.common.rpc.proto.BinaryAdapter;
import com.inductiveautomation.ignition.common.rpc.proto.DeserializationContext;
import com.inductiveautomation.ignition.common.rpc.proto.ProtoSerializationException;
import com.inductiveautomation.ignition.common.rpc.proto.SerializationContext;
import com.inductiveautomation.ignition.common.rpc.proto.gen.Value;
import org.python.core.PyDictionary;

import static com.inductiveautomation.ignition.common.rpc.proto.ObjectSerializers.UNSAFE_OBJECT;

/**
 * Adapter for PyDictionary to be serialized and deserialized by the ProtoSerializer. Required because
 * ProtoRpcSerializer.DEFAULT_INSTANCE has no built-in support for Jython types - without this,
 * invokeFunction's PyDictionary parameter fails to cross the RPC boundary from Client/Designer scope.
 */
public class ScriptFunctionsPyDictionaryProtoAdapter implements BinaryAdapter<PyDictionary> {

    @Override
    public byte[] encode(PyDictionary any, SerializationContext context) throws ProtoSerializationException {
        var mapBuilder = Value.ValueCollection.newBuilder();

        for (Object key : any.keys()) {
            var value = any.get(key);

            var encodedKey = UNSAFE_OBJECT.encode(key);
            var encodedValue = UNSAFE_OBJECT.encode(value);

            var primaryKey = Value.newBuilder().setBinaryValue(ByteString.copyFrom(encodedKey)).build();
            var primaryValue = Value.newBuilder().setBinaryValue(ByteString.copyFrom(encodedValue)).build();

            mapBuilder
                    .addValue(primaryKey)
                    .addValue(primaryValue);
        }

        return mapBuilder.build().toByteArray();
    }

    @Override
    public PyDictionary decode(byte[] bytes, DeserializationContext context) throws ProtoSerializationException {
        var dictionary = new PyDictionary();

        try {
            var values = Value.ValueCollection.parseFrom(bytes);

            for (int i = 0; i < values.getValueCount(); i += 2) {
                var primaryKey = values.getValue(i).getBinaryValue();
                var primaryValue = values.getValue(i + 1).getBinaryValue();

                var keyObject = UNSAFE_OBJECT.decode(primaryKey.toByteArray());
                var valueObject = UNSAFE_OBJECT.decode(primaryValue.toByteArray());

                dictionary.put(keyObject, valueObject);
            }

            return dictionary;
        } catch (InvalidProtocolBufferException e) {
            throw new ProtoSerializationException("Unable to parse serialized byte array", e);
        }
    }
}
