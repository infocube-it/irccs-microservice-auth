package org.fhir.auth.irccs.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.fhir.auth.irccs.entity.User;


@Path("/fhir/auth/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface UserController {

    @GET
    Response getAllUsers(@QueryParam("email") @DefaultValue("") String email);

    @POST
    Response createUser(User user);

    @PUT
    Response updateUser(User user);

    @DELETE
    Response deleteUser(@QueryParam("email") String email);
}