package org.fhir.auth.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.fhir.auth.entity.Group;
import org.fhir.auth.service.GroupService;

public class GroupControllerImpl implements GroupController {

    @Inject
    GroupService groupService;

    public Response getAllGroups(String name) {
        return groupService.getAllGroups(name);
    }

    public Response createGroup(Group group) {
        return groupService.createGroup(group);
    }

    public Response updateGroup(Group group) {
        return groupService.updateGroup(group);
    }

    public Response deleteGroup(String name) {
        return groupService.deleteGroup(name);
    }

}