package com.verifyinboxes.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** The JSON of a model, for its {@code toString()}. */
final class ModelJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModelJson() {
    }

    /** The model as JSON, without the fields named (secrets shown only once). */
    static String write(Object model, String... without) {
        try {
            JsonNode tree = MAPPER.valueToTree(model);
            if (tree instanceof ObjectNode) {
                for (String field : without) {
                    ((ObjectNode) tree).remove(field);
                }
            }
            return MAPPER.writeValueAsString(tree);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return model.getClass().getSimpleName();
        }
    }
}
