package com.example.demo;

import com.google.protobuf.Descriptors;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import lombok.Value;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class FieldMaskMatcher {
    FieldMask fieldMask;
    Class<? extends Message> root;
    List<Field> defaultFields;

    public static FieldMaskMatcher of(FieldMask fieldMask, Class<? extends Message> root) {
        return new FieldMaskMatcher(fieldMask, root, List.of());
    }

    private FieldMaskMatcher(FieldMask fieldMask, Class<? extends Message> root, List<Field> defaultFields) {
        this.fieldMask = fieldMask;
        this.root = root;
        this.defaultFields = defaultFields;
    }

    public String currentPath() {
        return getPath(defaultFields);
    }

    public boolean match(String path) {
        return fieldMask.getPathsList().stream()
                .anyMatch(req -> path.matches(req) || path.matches(req + "\\..*"));
    }

    public boolean match(int fieldNumber) {
        var path = getPath(concat(defaultFields, List.of(new MessageField(fieldNumber))));
        return match(path);
    }

    public boolean match(int index, int fieldNumber) {
        var fields = new ArrayList<Field>();
        fields.add(new RepeatedMessageField(index, fieldNumber));
        var path = getPath(concat(defaultFields, fields));
        return match(path);
    }

    public FieldMaskMatcher addFieldNumber(int fieldNumber) {
        return new FieldMaskMatcher(
                fieldMask,
                root,
                concat(defaultFields, List.of(new MessageField(fieldNumber)))
        );
    }

    private String getPath(List<Field> fields) {
        if (fields.size() == 0) {
            return "";
        }
        return traversePath(getDefaultInstance(root).getDescriptorForType(), fields, 0);
    }

    private String traversePath(Descriptors.Descriptor descriptor, List<Field> fields, int index) {
        var field = fields.get(index);
        var descriptorField = descriptor.findFieldByNumber(field.fieldNumber());
        var fieldName = getString(field) + descriptorField.getName();
        if (fields.size() == (index + 1)) {
            return fieldName;
        } else {
            return fieldName
                    + "."
                    + traversePath(descriptorField.getMessageType(), fields, index + 1);
        }
    }

    private static String getString(Field field) {
        return (field instanceof RepeatedMessageField) ? ((RepeatedMessageField) field).getIndex() + "." : "";
    }

    <T> List<T> concat(List<T> list1, List<T> list2) {
        var list = new ArrayList<T>(list1);
        list.addAll(list2);
        return list;
    }

    /**
     * @see com.google.protobuf.Internal#getDefaultInstance
     */
    @SuppressWarnings("unchecked")
    private <T extends MessageLite> T getDefaultInstance(Class<T> clazz) {
        try {
            Method method = clazz.getMethod("getDefaultInstance");
            return (T) method.invoke(method);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get default instance for " + clazz, e);
        }
    }

    interface Field {
        int fieldNumber();
    }

    @Value
    static class MessageField implements Field {
        int filedNumber;

        @Override
        public int fieldNumber() {
            return filedNumber;
        }
    }

    @Value
    static class RepeatedMessageField implements Field {
        int index;
        int filedNumber;

        @Override
        public int fieldNumber() {
            return filedNumber;
        }
    }
}
