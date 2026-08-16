/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.bridge.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight JSON schema generator for Java record types and POJOs.
 *
 * <p>Produces standard JSON Schema specifications (with {@code type}, {@code properties},
 * and {@code required} fields) used to constrain LLM responses.</p>
 */
public final class JsonSchemaGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemaGenerator() {
        // utility class
    }

    /**
     * Generates a JSON schema string for the specified target class.
     *
     * @param targetClass the Java class or record to introspect
     * @return standard JSON Schema string
     */
    public static String generateSchemaJson(Class<?> targetClass) {
        return generateSchemaJson(targetClass, false);
    }

    /**
     * Generates a JSON schema string for the specified target class with formatting options.
     *
     * @param targetClass the Java class or record to introspect
     * @param prettyPrint whether to format with indentation
     * @return standard JSON Schema string
     */
    public static String generateSchemaJson(Class<?> targetClass, boolean prettyPrint) {
        ObjectNode schema = generateSchemaNode(targetClass);
        try {
            return prettyPrint
                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema)
                    : MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            throw new StructuredOutputException("Failed to serialize generated JSON schema for " + targetClass.getName(), e);
        }
    }

    /**
     * Generates a Jackson {@link ObjectNode} representing the JSON Schema.
     *
     * @param targetClass the Java class or record to introspect
     * @return schema ObjectNode
     */
    public static ObjectNode generateSchemaNode(Class<?> targetClass) {
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        ObjectNode root = MAPPER.createObjectNode();
        root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        root.put("title", targetClass.getSimpleName());

        populateTypeSchema(root, targetClass, null, new HashSet<>());
        return root;
    }

    private static void populateTypeSchema(ObjectNode node, Class<?> type, Type genericType, Set<Class<?>> visited) {
        if (type.isEnum()) {
            node.put("type", "string");
            ArrayNode enumValues = node.putArray("enum");
            for (Object constant : type.getEnumConstants()) {
                enumValues.add(constant.toString());
            }
            return;
        }

        if (String.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type) || type == char.class
                || UUID.class.isAssignableFrom(type) || Instant.class.isAssignableFrom(type)
                || LocalDate.class.isAssignableFrom(type) || LocalDateTime.class.isAssignableFrom(type)) {
            node.put("type", "string");
            return;
        }

        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            node.put("type", "integer");
            return;
        }

        if (type == float.class || type == Float.class || type == double.class || type == Double.class
                || Number.class.isAssignableFrom(type)) {
            node.put("type", "number");
            return;
        }

        if (type == boolean.class || type == Boolean.class) {
            node.put("type", "boolean");
            return;
        }

        if (type.isArray()) {
            node.put("type", "array");
            ObjectNode itemsNode = node.putObject("items");
            populateTypeSchema(itemsNode, type.getComponentType(), null, visited);
            return;
        }

        if (Collection.class.isAssignableFrom(type)) {
            node.put("type", "array");
            ObjectNode itemsNode = node.putObject("items");
            Class<?> itemType = Object.class;
            if (genericType instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> clz) {
                    itemType = clz;
                }
            }
            populateTypeSchema(itemsNode, itemType, null, visited);
            return;
        }

        if (Map.class.isAssignableFrom(type)) {
            node.put("type", "object");
            node.put("additionalProperties", true);
            return;
        }

        // Object / Record
        node.put("type", "object");
        if (visited.contains(type)) {
            // Prevent circular reference recursion
            return;
        }
        visited.add(type);

        ObjectNode propertiesNode = node.putObject("properties");
        ArrayNode requiredNode = MAPPER.createArrayNode();

        if (type.isRecord()) {
            for (RecordComponent comp : type.getRecordComponents()) {
                String name = comp.getName();
                ObjectNode propNode = propertiesNode.putObject(name);
                populateTypeSchema(propNode, comp.getType(), comp.getGenericType(), new HashSet<>(visited));
                requiredNode.add(name);
            }
        } else {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                ObjectNode propNode = propertiesNode.putObject(name);
                populateTypeSchema(propNode, field.getType(), field.getGenericType(), new HashSet<>(visited));
                requiredNode.add(name);
            }
        }

        if (!requiredNode.isEmpty()) {
            node.set("required", requiredNode);
        }
        node.put("additionalProperties", false);
    }
}
