package voldemort.serialization.json;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import voldemort.serialization.SerializationException;

/**
 * A Java collections object that represents the expected type
 * 
 * @author jay
 * 
 */
public class JsonTypeDefinition implements Serializable {

    private static final long serialVersionUID = 1;

    public final static JsonTypeDefinition INT8 = JsonTypeDefinition.fromJson("\"int8\"");
    public final static JsonTypeDefinition INT16 = JsonTypeDefinition.fromJson("\"int16\"");
    public final static JsonTypeDefinition INT32 = JsonTypeDefinition.fromJson("\"int32\"");
    public final static JsonTypeDefinition INT64 = JsonTypeDefinition.fromJson("\"int64\"");
    public final static JsonTypeDefinition FLOAT32 = JsonTypeDefinition.fromJson("\"float32\"");
    public final static JsonTypeDefinition FLOAT64 = JsonTypeDefinition.fromJson("\"float64\"");
    public final static JsonTypeDefinition STRING = JsonTypeDefinition.fromJson("\"string\"");
    public final static JsonTypeDefinition DATE = JsonTypeDefinition.fromJson("\"date\"");
    public final static JsonTypeDefinition BOOLEAN = JsonTypeDefinition.fromJson("\"boolean\"");

    private Object type;

    public JsonTypeDefinition(Object type) {
        this.type = type;
        validate(type);
    }

    public static JsonTypeDefinition fromJson(String typeSig) {
        if(typeSig == null)
            throw new IllegalArgumentException("The type signiture for a JsonTypeDefinition cannot be null!");
        JsonReader reader = new JsonReader(new StringReader(typeSig));
        Object result = reader.read();
        return new JsonTypeDefinition(fromJsonObjects(result));
    }

    @SuppressWarnings("unchecked")
    private static Object fromJsonObjects(Object o) {
        if(o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            Map<String, Object> newM = new LinkedHashMap<String, Object>(m.size());
            List<String> keys = new ArrayList<String>((m.keySet()));
            Collections.sort(keys);
            for(String key: keys)
                newM.put(key, fromJsonObjects(m.get(key)));
            return newM;
        } else if(o instanceof List) {
            List<?> l = (List<?>) o;
            if(l.size() != 1)
                throw new SerializationException("List type must have a single entry specifying entry type.");
            List<Object> newL = new ArrayList<Object>(1);
            newL.add(fromJsonObjects(l.get(0)));
            return newL;
        } else if(o instanceof String) {
            return JsonTypes.fromDisplay((String) o);
        } else {
            throw new SerializationException(o + " is not a string, an array, or an object, "
                                             + "so it is not valid in a type definition.");
        }
    }

    /**
     * Get the type created by selecting only a subset of properties from this
     * type. The type must be a map for this to work
     * 
     * @param properties The properties to select
     * @return The new type definition
     */
    public JsonTypeDefinition projectionType(String... properties) {
        if(this.getType() instanceof Map) {
            Map<String, Object> type = (Map<String, Object>) getType();
            Map<String, Object> newType = new HashMap<String, Object>();
            for(String prop: properties)
                newType.put(prop, type.get(prop));
            return new JsonTypeDefinition(newType);
        } else {
            throw new IllegalArgumentException("Cannot take the projection of a type that is not a Map.");
        }
    }

    public JsonTypeDefinition subtype(String field) {
        if(this.getType() instanceof Map) {
            Map<String, Object> type = (Map<String, Object>) getType();
            return new JsonTypeDefinition(type.get(field));
        } else {
            throw new IllegalArgumentException("Cannot take the projection of a type that is not a Map.");
        }
    }

    public Object getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return format(type);
    }

    public static String format(Object type) {
        StringBuilder b = new StringBuilder();
        if(type instanceof JsonTypes) {
            JsonTypes t = (JsonTypes) type;
            b.append('"');
            b.append(t.toDisplay());
            b.append('"');
        } else if(type instanceof List) {
            b.append('[');
            List<?> l = (List<?>) type;
            for(Object o: l)
                b.append(format(o));
            b.append(']');
        } else if(type instanceof Map) {
            b.append('{');
            Map<?, ?> m = (Map<?, ?>) type;
            int i = 0;
            for(Map.Entry<?, ?> e: m.entrySet()) {
                b.append('"');
                b.append(e.getKey());
                b.append('"');
                b.append(':');
                b.append(format(e.getValue()));
                if(i < m.size() - 1)
                    b.append(", ");
                i++;
            }
            b.append('}');
        } else {
            throw new SerializationException("Current type is " + type + " of class "
                                             + type.getClass() + " which is not allowed.");
        }

        return b.toString();
    }

    public void validate() {
        validate(getType());
    }

    private void validate(Object type) {
        if(type == null) {
            throw new IllegalArgumentException("Type or subtype cannot be null.");
        } else if(type instanceof List) {
            List<Object> l = (List<Object>) type;
            if(l.size() != 1)
                throw new IllegalArgumentException("Lists in type definition must have length exactly one.");
            validate(l.get(0));
        } else if(type instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) type;
            for(Map.Entry<String, Object> entry: m.entrySet())
                validate(entry.getValue());
        } else if(type instanceof JsonTypes) {
            // this is good
        } else {
            throw new IllegalArgumentException("Unknown type in json type definition: " + type
                                               + " of class " + type.getClass().getName());
        }
    }
}
