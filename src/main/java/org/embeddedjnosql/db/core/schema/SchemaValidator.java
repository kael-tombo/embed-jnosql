package org.embeddedjnosql.db.core.schema;

import java.util.*;

public class SchemaValidator {

    private final Map<String, SchemaDefinition> schemas;

    public SchemaValidator() {
        this.schemas = new HashMap<>();
    }

    public SchemaDefinition getSchema(String collection) {
        return schemas.get(collection);
    }

    public void registerSchema(String collection, SchemaDefinition schema) {
        schemas.put(collection, schema);
    }

    public void dropSchema(String collection) {
        schemas.remove(collection);
    }

    public ValidationResult validate(String collection, Map<String, Object> data) {
        var schema = schemas.get(collection);
        if (schema == null) {
            return ValidationResult.valid();
        }
        return schema.validate(data);
    }

    public boolean hasSchema(String collection) {
        return schemas.containsKey(collection);
    }

    public Collection<String> getSchemaNames() {
        return Collections.unmodifiableCollection(schemas.keySet());
    }

    public static class SchemaDefinition {
        private final String collectionName;
        private final List<FieldDefinition> fields;
        private final boolean strict;

        public SchemaDefinition(String collectionName) {
            this(collectionName, new ArrayList<>(), false);
        }

        public SchemaDefinition(String collectionName, List<FieldDefinition> fields, boolean strict) {
            this.collectionName = collectionName;
            this.fields = fields;
            this.strict = strict;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public List<FieldDefinition> getFields() {
            return fields;
        }

        public boolean isStrict() {
            return strict;
        }

        public SchemaDefinition field(String name, Class<?> type, boolean required) {
            fields.add(new FieldDefinition(name, type, required));
            return this;
        }

        public SchemaDefinition field(String name, Class<?> type) {
            return field(name, type, false);
        }

        public ValidationResult validate(Map<String, Object> data) {
            var errors = new ArrayList<String>();

            for (var field : fields) {
                Object value = data.get(field.name);

                if (field.required && value == null) {
                    errors.add("Required field '" + field.name + "' is missing");
                    continue;
                }

                if (value != null && !isTypeCompatible(field.type, value)) {
                    errors.add("Field '" + field.name + "' must be of type " + field.type.getSimpleName() + 
                            ", got " + value.getClass().getSimpleName());
                }
            }

            if (strict) {
                for (var key : data.keySet()) {
                    if (fields.stream().noneMatch(f -> f.name.equals(key))) {
                        errors.add("Unknown field '" + key + "' in strict mode");
                    }
                }
            }

            return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
        }

        /**
         * JSON has a single number type, so any {@link Number} satisfies a numeric field
         * type. Before this check, a field registered as {@code number} (mapped to
         * {@code Double}) rejected integral values such as {@code 99} with
         * "must be of type Double, got Integer" (audit R-32).
         */
        private static boolean isTypeCompatible(Class<?> declared, Object value) {
            if (declared.isInstance(value)) {
                return true;
            }
            return Number.class.isAssignableFrom(declared) && value instanceof Number;
        }

        private static class FieldDefinition {
            final String name;
            final Class<?> type;
            final boolean required;

            FieldDefinition(String name, Class<?> type, boolean required) {
                this.name = name;
                this.type = type;
                this.required = required;
            }
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    public static SchemaDefinition builder(String collectionName) {
        return new SchemaDefinition(collectionName);
    }
}
