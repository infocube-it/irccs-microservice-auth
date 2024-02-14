package org.fhir.auth.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.fhir.auth.entity.User;
import org.fhir.auth.service.UserService;

public class UserControllerImpl implements UserController{

    @Inject
    UserService userService;

    public Response getAllUsers(String email) {
        return userService.getAllUsers(email);
    }

    public Response createUser(User user) {
        return userService.createUser(user);
    }

    public Response updateUser(User user) {
        return userService.updateUser(user);
    }

    public Response deleteUser(String email) {
        return userService.deleteUser(email);
    }

}