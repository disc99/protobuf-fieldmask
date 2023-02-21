package com.example.demo;

import io.example.user.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.Value;
import org.lognet.springboot.grpc.GRpcService;

import java.util.*;
import java.util.function.BiFunction;

@GRpcService
public class UserGrpc extends UserServiceGrpc.UserServiceImplBase {

    DatabaseMock db = new DatabaseMock();

    @Override
    public void createUser(CreateUserRequest request, StreamObserver<User> responseObserver) {
        var requestUser = request.getUser();
        var contact = requestUser.getContact();
        var domainUser = new DomainUser(
                requestUser.getName(),
                requestUser.getPassword(),
                new DomainContact(contact.getTel(), contact.getMail()),
                map(requestUser.getBooksList(), (it, i) -> new DomainBook(it.getName(), it.getAuthor()))
        );
        db.insert(domainUser);
        responseObserver.onNext(requestUser);
        responseObserver.onCompleted();
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<User> responseObserver) {
        updateUserString(request, responseObserver); // Use paths string case
        // updateUserUtil(request, responseObserver); // Use FieldMask Util class case
    }

    private void updateUserString(UpdateUserRequest request, StreamObserver<User> responseObserver) {
        var updateMask = request.getUpdateMask().getPathsList();
        var requestUser = request.getUser();
        var domainUser = db.select(requestUser.getName());

        if (domainUser == null) {
            responseObserver.onError(Status.NOT_FOUND.asException());

        } else if (updateMask.contains("name")) {
            // Unsupported paths return INVALID_ARGUMENT.(https://google.aip.dev/161#invalid-field-mask-entries)
            responseObserver.onError(Status.INVALID_ARGUMENT.asException());

        } else {
            var requestContact = requestUser.getContact();
            var domainContact = domainUser.getContact();
            var newDomainUser = new DomainUser(
                    domainUser.getId(),
                    updateMask.contains("password") ? requestUser.getPassword() : domainUser.getPassword(),
                    updateMask.contains("contact") ? (
                            requestUser.hasContact() ? new DomainContact(
                                    updateMask.contains("contact.tel") ? requestContact.getTel() : domainContact.getTel(),
                                    updateMask.contains("contact.mail") ? requestContact.getMail() : domainContact.getMail()
                            ) : null
                    ) : domainUser.getContact(),
                    updateMask.contains("books") ?
                            map(requestUser.getBooksList(), (it, i) -> {
                                var domainBook = domainUser.getBooks().get(i);
                                return new DomainBook(
                                        (updateMask.contains("books.*.name") || updateMask.contains("books." + i + ".name"))
                                                ? it.getName() : domainBook.getName(),
                                        (updateMask.contains("books.*.author") || updateMask.contains("books." + i + ".author"))
                                                ? it.getAuthor() : domainBook.getAuthor()
                                );
                            }) : domainUser.getBooks()
            );
            db.update(newDomainUser);
            responseObserver.onNext(requestUser);
        }

        responseObserver.onCompleted();
    }

    private void updateUserUtil(UpdateUserRequest request, StreamObserver<User> responseObserver) {
        var requestUser = request.getUser();
        var userMask = FieldMaskMatcher.of(request.getUpdateMask(), User.class);
        var contactMask = userMask.addFieldNumber(User.CONTACT_FIELD_NUMBER);
        var booksMask = userMask.addFieldNumber(User.BOOKS_FIELD_NUMBER);
        var domainUser = db.select(requestUser.getName());

        if (domainUser == null) {
            responseObserver.onError(Status.NOT_FOUND.asException());

        } else if (userMask.match(User.NAME_FIELD_NUMBER)) {
            // Unsupported paths return INVALID_ARGUMENT.(https://google.aip.dev/161#invalid-field-mask-entries)
            responseObserver.onError(Status.INVALID_ARGUMENT.asException());

        } else {
            var requestContact = requestUser.getContact();
            var domainContact = domainUser.getContact();
            var newDomainUser = new DomainUser(
                    domainUser.getId(),
                    userMask.match(User.PASSWORD_FIELD_NUMBER) ? requestUser.getPassword() : domainUser.getPassword(),
                    userMask.match(User.CONTACT_FIELD_NUMBER) ? (
                            requestUser.hasContact() ? new DomainContact(
                                    contactMask.match(Contact.TEL_FIELD_NUMBER) ? requestContact.getTel() : domainContact.getTel(),
                                    contactMask.match(Contact.MAIL_FIELD_NUMBER) ? requestContact.getMail() : domainContact.getMail()
                            ) : null
                    ) : domainUser.getContact(),
                    userMask.match(User.BOOKS_FIELD_NUMBER) ?
                            map(requestUser.getBooksList(), (it, i) -> {
                                var domainBook = domainUser.getBooks().get(i);
                                return new DomainBook(
                                        booksMask.match(i, Book.NAME_FIELD_NUMBER) ? it.getName() : domainBook.getName(),
                                        booksMask.match(i, Book.AUTHOR_FIELD_NUMBER) ? it.getAuthor() : domainBook.getAuthor()
                                );
                            })
                            : domainUser.getBooks()
            );
            db.update(newDomainUser);
            responseObserver.onNext(requestUser);
        }

        responseObserver.onCompleted();
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {
        var domainUser = db.select(request.getName());
        if (domainUser == null) {
            responseObserver.onError(Status.NOT_FOUND.asException());
        } else {
            var user = User.newBuilder()
                    .setName(domainUser.id)
                    .setPassword(domainUser.password)
                    .setContact(Contact.newBuilder()
                            .setTel(domainUser.contact.tel)
                            .setMail(domainUser.contact.mail)
                            .build())
                    .build();
            responseObserver.onNext(user);
        }
        responseObserver.onCompleted();
    }

    // Stream#map shortcut
    <T, R> List<R> map(List<T> list, BiFunction<T, Integer, R> mapper) {
        var result = new ArrayList<R>();
        for (int i = 0; i < list.size(); i++) {
            result.add(mapper.apply(list.get(i), i));
        }
        return result;
    }

    @Value
    static class DomainUser {
        String id;
        String password;
        DomainContact contact;
        List<DomainBook> books;
    }

    @Value
    static class DomainContact {
        String tel;
        String mail;
    }

    @Value
    static class DomainBook {
        String name;
        String author;
    }

    static class DatabaseMock {
        Map<String, DomainUser> map = new HashMap<>();

        DomainUser select(String userName) {
            return map.get(userName);
        }

        void insert(DomainUser user) {
            map.put(user.getId(), user);
        }

        void update(DomainUser user) {
            map.put(user.getId(), user);
        }
    }
}

