package com.ausiankou.exception;

public class CustomExceptions {

    public static class ResourceNotFoundException extends RuntimeException {
        private final String resourceName;
        private final String fieldName;
        private final Object fieldValue;

        public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
            super(String.format("%s не найден с %s: '%s'", resourceName, fieldName, fieldValue));
            this.resourceName = resourceName;
            this.fieldName = fieldName;
            this.fieldValue = fieldValue;
        }
    }

    public static class DuplicateResourceException extends RuntimeException {
        private final String resourceName;
        private final String fieldName;
        private final Object fieldValue;

        public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
            super(String.format("%s с %s '%s' уже существует", resourceName, fieldName, fieldValue));
            this.resourceName = resourceName;
            this.fieldName = fieldName;
            this.fieldValue = fieldValue;
        }
    }

    public static class BusinessRuleException extends RuntimeException {
        private final String rule;

        public BusinessRuleException(String rule, String message) {
            super(message);
            this.rule = rule;
        }
    }

    public static class InvalidDataException extends RuntimeException {
        private final String field;

        public InvalidDataException(String field, String message) {
            super(message);
            this.field = field;
        }
    }

    public static class UnauthorizedActionException extends RuntimeException {
        public UnauthorizedActionException(String message) {
            super(message);
        }
    }
}