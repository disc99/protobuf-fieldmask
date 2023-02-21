package com.example.demo;

import com.example.demo.FieldMaskMatcher.MessageField;
import com.example.demo.FieldMaskMatcher.RepeatedMessageField;
import com.google.protobuf.FieldMask;
import io.example.user.Book;
import io.example.user.Contact;
import io.example.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FieldMaskMatcherTest {

    @Test
    void test() {
        var input = FieldMask.newBuilder()
                .addPaths("name")
                .addPaths("contact.tel")
                .addPaths("books.1.name")
                .addPaths("books.*.author")
                .build();

        var userMatcher = FieldMaskMatcher.of(input, User.class);
        assertEquals(userMatcher.currentPath(), "");
        assertTrue(userMatcher.match(User.NAME_FIELD_NUMBER));
        assertFalse(userMatcher.match(User.PASSWORD_FIELD_NUMBER));

        // nested filed
        var contactMatcher = userMatcher.addFieldNumber(User.CONTACT_FIELD_NUMBER);
        assertEquals(userMatcher.currentPath(), ""); // matcher class is immutable
        assertEquals(contactMatcher.currentPath(), "contact");
        assertTrue(contactMatcher.match(Contact.TEL_FIELD_NUMBER));
        assertFalse(contactMatcher.match(Contact.MAIL_FIELD_NUMBER));

        var mailMatcher = contactMatcher.addFieldNumber(Contact.MAIL_FIELD_NUMBER);
        assertEquals(mailMatcher.currentPath(), "contact.mail");

        // Array field
        var booksFieldMask = userMatcher.addFieldNumber(User.BOOKS_FIELD_NUMBER);
        assertEquals(booksFieldMask.currentPath(), "books");
        assertFalse(booksFieldMask.match(0, Book.NAME_FIELD_NUMBER));
        assertTrue(booksFieldMask.match(1, Book.NAME_FIELD_NUMBER));
        assertTrue(booksFieldMask.match(0, Book.AUTHOR_FIELD_NUMBER));
        assertTrue(booksFieldMask.match(100, Book.AUTHOR_FIELD_NUMBER));
    }

    @ParameterizedTest
    @CsvSource({
        "name,         name,           true",
        "name,         namex,          false",
        "contact.tel,  contact.tel,    true",
        "contact.tel,  contact.mail,   false",
        "contact,      contact.tel,    true",
        "books.1.name, books.0.name,   false",
        "books.1.name, books.1.name,   true",
        "books.*.name, books.0.name,   true",
        "books.*.name, books.100.name, true",
    })
    void testMatch(String path, String checkString, boolean result) {
        var input = FieldMask.newBuilder()
                .addPaths(path)
                .build();
        var matcher = FieldMaskMatcher.of(input, User.class);
        assertEquals(matcher.match(checkString), result);
    }

    @ParameterizedTest
    @MethodSource("pathData")
    void testPath(String path, List<FieldMaskMatcher.Field> fields) {
        var matcher = FieldMaskMatcher.of(FieldMask.newBuilder().build(), User.class);
        matcher.defaultFields = fields;
        var currentPath = matcher.currentPath();
        assertEquals(currentPath, path);
    }

    static Stream<Arguments> pathData() {
        return Stream.of(
                arguments("name", List.of(new MessageField(User.NAME_FIELD_NUMBER))),
                arguments("contact.mail", List.of(new MessageField(User.CONTACT_FIELD_NUMBER), new MessageField(Contact.MAIL_FIELD_NUMBER))),
                arguments("books.1.author", List.of(new MessageField(User.BOOKS_FIELD_NUMBER), new RepeatedMessageField(1, Book.AUTHOR_FIELD_NUMBER)))
        );
    }
}