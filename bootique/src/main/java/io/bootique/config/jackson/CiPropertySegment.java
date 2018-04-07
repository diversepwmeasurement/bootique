package io.bootique.config.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map.Entry;

/**
 * A path segment for case-insensitive path.
 */
class CiPropertySegment extends PropertyPathSegment {

    static PathSegment<?> create(JsonNode node, String path) {

        if (path.length() == 0) {
            return new LastPathSegment(node, null, null);
        }

        if (path.charAt(0) == ARRAY_INDEX_START) {
            return new IndexPathSegment(toArrayNode(node), null, null, path);
        }

        return new CiPropertySegment(toObjectNode(node), null, null, path);
    }

    protected CiPropertySegment(ObjectNode node, PathSegment parent, String incomingPath, String remainingPath) {
        super(node, parent, incomingPath, remainingPath);
    }

    @Override
    protected JsonNode readChild(String childName) {
        String key = getNode() != null ? getChildCiKey(getNode(), childName) : childName;
        return getNode() != null ? getNode().get(key) : null;
    }

    @Override
    protected PathSegment<ArrayNode> createIndexedChild(String childName, String remainingPath) {
        throw new UnsupportedOperationException("Indexed CI children are unsupported");
    }

    @Override
    protected PathSegment<ObjectNode> createPropertyChild(String childName, String remainingPath) {
        ObjectNode on = toObjectNode(readChild(childName));
        return new CiPropertySegment(on, this, childName, remainingPath);
    }

    private String getChildCiKey(JsonNode parent, String fieldName) {

        fieldName = fieldName.toUpperCase();

        Iterator<Entry<String, JsonNode>> fields = parent.fields();
        while (fields.hasNext()) {
            Entry<String, JsonNode> f = fields.next();
            if (fieldName.equalsIgnoreCase(f.getKey())) {
                return f.getKey();
            }
        }

        return fieldName;
    }

}
