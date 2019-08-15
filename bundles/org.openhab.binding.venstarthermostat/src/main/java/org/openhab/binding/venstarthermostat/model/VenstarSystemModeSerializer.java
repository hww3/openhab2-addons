package org.openhab.binding.venstarthermostat.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class VenstarSystemModeSerializer implements JsonDeserializer<VenstarSystemMode> {
    @Override
    public VenstarSystemMode deserialize(JsonElement element, Type arg1, JsonDeserializationContext arg2) throws JsonParseException {
        int key = element.getAsInt();
        return VenstarSystemMode.fromInt(key);
    }
}
