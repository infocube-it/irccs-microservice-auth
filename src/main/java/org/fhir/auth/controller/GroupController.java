package org.fhir.auth.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.fhir.auth.entity.Group;


@Path("/fhir/auth/groups")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface GroupController {

    @GET
    Response getAllGroups(@QueryParam("name") @DefaultValue("") String name);

    @POST
    Response createGroup(Group group);

    @PUT
    Response updateGroup(Group group);

    @DELETE
    Response deleteGroup(@QueryParam("name") String name);
}
