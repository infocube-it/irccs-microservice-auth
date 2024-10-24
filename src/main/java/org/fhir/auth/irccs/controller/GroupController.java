package org.fhir.auth.irccs.controller;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.fhir.auth.irccs.entity.Group;


@Path("/fhir/auth/groups")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public interface GroupController {

    @GET
    Response getAllGroups(@QueryParam("name") @DefaultValue("") String name);

    @POST
    Response createGroup(Group group);

    @PUT
    Response updateGroup(Group group);

    @DELETE
    Response deleteGroup(String id);
}
