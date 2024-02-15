package org.fhir.auth.irccs.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.fhir.auth.irccs.entity.PermissionWrapper;
import org.fhir.auth.irccs.service.PermissionService;

public class PermissionControllerImpl implements PermissionController{

    @Inject
    PermissionService permissionService;

 /*   public Response getPermission(String name) {
        return permissionService.getPermission(name);
    }*/

    public Response createPermission(PermissionWrapper permissions) {
        return permissionService.createPermission(permissions);
    }

   /* public Response updatePermission(PermissionWrapper permissions) {
        return permissionService.createPermission(permissions);
    }*/
}