package org.fhir.auth.irccs.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.fhir.auth.irccs.entity.User;
import org.fhir.auth.irccs.service.UserService;

public class UserControllerImpl implements UserController{

    @Inject
    UserService userService;

    public Response getAllUsers(String email) {
        return userService.getAllUsers(email);
    }

    public Response createUser(User user) {
        return userService.createKeycloakUser(user);
    }

    public Response enableUser(String email) {
        return userService.enableUser(email);
    }

    public Response updateUser(User user) {
        return userService.updateUser(user);
    }

    public Response deleteUser(String email) {
        return userService.deleteUser(email);
    }

}
