package org.openhab.binding.venstarthermostat.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class VenstarSystemStateSerializer implements JsonDeserializer<VenstarSystemState> {
    @Override
    public VenstarSystemState deserialize(JsonElement element, Type arg1, JsonDeserializationContext arg2) throws JsonParseException {
        int key = element.getAsInt();
        return VenstarSystemState.fromInt(key);
    }
}