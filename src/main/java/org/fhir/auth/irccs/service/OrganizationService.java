package org.fhir.auth.irccs.service;


import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.fhir.auth.irccs.RollbackSystem.Command;
import org.fhir.auth.irccs.RollbackSystem.RollbackManager;
import org.fhir.auth.irccs.entity.OrganizationRequest;
import org.fhir.auth.irccs.entity.User;
import org.fhir.auth.irccs.exceptions.OperationException;
import org.hl7.fhir.r5.model.*;
import org.jboss.resteasy.reactive.RestResponse;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.quarkus.irccs.client.restclient.FhirClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@ApplicationScoped
public class OrganizationService {
    private final static Logger LOG = LoggerFactory.getLogger(OrganizationService.class);
    @Inject
    KeycloakService keycloakService;
    @RestClient
    OrganizationClient organizationClient;

    @Inject
    FhirClient<Bundle> bundleClient;

    public List<OrganizationRequest> getOrganizations(Integer count, Integer offset, String name){
            Response token = keycloakService.getAdminToken();
            if(token.hasEntity()) {
                String jwtToken = "Bearer " + token.readEntity(AccessTokenResponse.class).getToken();
                Bundle organizations;
                if(name.isEmpty()){
                    organizations = bundleClient.parseResource(Bundle.class, organizationClient.getOrganizations(jwtToken, count, offset));
                } else {
                    organizations = bundleClient.parseResource(Bundle.class, organizationClient.getOrganizationsByName(jwtToken, count, offset, name));
                }
                if(organizations.getEntry().size() > 0){
                    return organizations.getEntry().stream()
                            .map(entry -> new OrganizationRequest(((Organization) entry.getResource()).getName(), ((Organization) entry.getResource()).getIdentifier().stream().filter(ext -> ext.getUse().equals(Identifier.IdentifierUse.SECONDARY)).toList().get(0).getValue())).toList();
                } else return new ArrayList<>();
            }
        return null;
    }

}

