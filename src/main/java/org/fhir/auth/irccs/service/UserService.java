package org.fhir.auth.irccs.service;


import ca.uhn.fhir.parser.DataFormatException;
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
import org.fhir.auth.irccs.entity.User;
import org.fhir.auth.irccs.exceptions.OperationException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.ContactPoint;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Practitioner;
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
public class UserService {
    private final static Logger LOG = LoggerFactory.getLogger(UserService.class);
    @Inject
    Keycloak keycloak;
    @Inject
    FhirClient<Practitioner> practitionerController;
    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm")
    String realm;
    @Inject
    KeycloakService keycloakService;

    @RestClient
    PractitionerClient practitionerClient;

    @Inject
    ReactiveMailer mailer;

    private RealmResource getRealm() {
        return keycloak.realm(realm);
    }

    public Response getAllUsers(String email) {
        if (email.isEmpty())
            return Response.ok(getRealm()
                            .users()
                            .list()
                            .stream()
                            .map(User::fromUserRepresentation)
                            .toList())
                    .build();

        return Response.ok(User.fromUserRepresentation(getUserByEmail_keycloak(email))).build();
    }

    public Response enableUser(String email) {
        RollbackManager rollbackManager = new RollbackManager();

        User user = User.fromUserRepresentation(getUserByEmail_keycloak(email));
        rollbackManager.addCommand(new Command(
                () -> enableKeycloakUser(email).close(),
                () -> disableKeycloakUser(email).close()
        ));

        rollbackManager.addCommand(new Command(
                () -> createFhirPractitioner(user).close(),
                () -> {}
        ));

        try {
            rollbackManager.executeCommands();
        } catch (Exception e){
            return Response.status(RestResponse.Status.EXPECTATION_FAILED).build();
        }

        user.setEnabled(true);
        return Response.ok(user).build();
    }

    public Response updateUser(User user) {
        RollbackManager rollbackManager = new RollbackManager();

        User foundUser = User.fromUserRepresentation(getUserByEmail_keycloak(user.getEmail()));
        rollbackManager.addCommand(new Command(
                () -> updateKeycloakUser(user).close(),
                () -> updateKeycloakUser(foundUser).close()
        ));

        rollbackManager.addCommand(new Command(
                () -> updateFhirPractitioner(user).close(),
                () -> {}
        ));

        try {
            rollbackManager.executeCommands();
        } catch (Exception e){
            return Response.status(RestResponse.Status.EXPECTATION_FAILED).build();
        }

        return Response.ok(user).build();
    }

    public Response deleteUser(String email) {
        RollbackManager rollbackManager = new RollbackManager();

        User foundUser = User.fromUserRepresentation(getUserByEmail_keycloak(email));

        rollbackManager.addCommand(new Command(
                () -> deleteKeycloakUser(email),
                () -> createKeycloakUser(foundUser).close()
        ));

        rollbackManager.addCommand(new Command(
                () -> {
                    try {
                        deleteFhirPractitioner(email);
                    } catch (IndexOutOfBoundsException e){
                        LOG.info("No such Fhir Practitioner: " + email);
                    }
                },
                () -> {}
        ));

        try {
            rollbackManager.executeCommands();
        } catch (Exception e){
            return Response.status(RestResponse.Status.EXPECTATION_FAILED).build();
        }

        return Response.ok().build();
    }

    public Response createKeycloakUser(User user) {
        try {

            UsersResource usersResource = getRealm().users();

            if(null != user.getPassword()){
                LOG.info("Creating SIGNUP Keycloak User: " + user.toString());
                user.setEnabled(false); //necessario per l'invio email di reset password
                UserRepresentation userRepresentation = User.toUserRepresentation(user);
                Response response = usersResource.create(userRepresentation);
                user.setId(CreatedResponseUtil.getCreatedId(response));
                getRealm().users().get(user.getId()).joinGroup(user.getOrganizationRequest().get(0));

                Objects.requireNonNull(user.getId(), "User ID cannot be null after creation.");

                // Prepare the credential for the user's password
                CredentialRepresentation credentialPassword = new CredentialRepresentation();
                credentialPassword.setTemporary(false);
                credentialPassword.setType(CredentialRepresentation.PASSWORD);
                credentialPassword.setValue(user.getPassword());
                UserResource userResource = usersResource.get(user.getId());
                Objects.requireNonNull(userResource, "UserResource cannot be null for password reset.");

                // Reset the user's password
                userResource.resetPassword(credentialPassword);

                LOG.info("Password set for Keycloak User: " + user.getEmail());
                mailer.send(
                        Mail.withText(user.getEmail(),
                                "WELCOME IN IRCCS - GESTIONE STUDI CLINICI ",
                                "La tua registrazione è andata a buon fine. A valle dell'abilitazione da parte " +
                                        "dell'amministratore di sistema, potrai accedere al portale irccs.infocube.it" +
                                        " \n Cordiali saluti"
                        )).subscribe().with(
                        success -> System.out.println("Email sent successfully to: " + user.getEmail()),
                        failure -> System.out.println("Failed to send email to: " + user.getEmail() + ", Reason: " + failure.getMessage())
                );
                ;

               /* azione non possibile per utenti disabilitati - va pensata una welcome mail
                getRealm().users().get(user.getId()).sendVerifyEmail();
                LOG.info("Send verify email");*/
            }else{
                LOG.info("Creating from admin Keycloak User: " + user.getEmail());
                //caso di registrazione del practiotioner no da signup ma tramite admin
                user.setEnabled(true); //necessario per l'invio email di reset password
                UserRepresentation userRepresentation = User.toUserRepresentation(user);
                Response response = usersResource.create(userRepresentation);
                user.setId(CreatedResponseUtil.getCreatedId(response));
                if( null != user.getOrganizationRequest()){
                    getRealm().users().get(user.getId()).joinGroup(user.getOrganizationRequest().get(0));
                }
                Objects.requireNonNull(user.getId(), "User ID cannot be null after creation.");

                LOG.info("Keycloak User created: " + user.getEmail() + ". Send reset password...");
                try {
                    // Esegui l'azione di reset della password
                    usersResource.get(user.getId()).executeActionsEmail(Arrays.asList("UPDATE_PASSWORD"));
                } catch (Exception e) {
                    LOG.error("ERROR: Couldn't send reset psw Keycloak User: {}.", user.getEmail(), e);
                    return Response.serverError().build();
                }
            }


            return Response.ok(user).build();
        } catch (Exception e) {
            LOG.error("Error creating Keycloak user: " + user.getEmail(), e);
            throw e;
        }
    }

    public Response enableKeycloakUser(String email) {
        LOG.info("Enabling user {}...", email);
        try {
            UserRepresentation userRepresentation = getUserByEmail_keycloak(email);
            Objects.requireNonNull(userRepresentation);
            userRepresentation.setEnabled(true);
            getRealm().users().get(userRepresentation.getId()).update(userRepresentation);
            return Response.ok(getUserByEmail_keycloak(email)).build();
        } catch (Exception e) {
            LOG.error("Error enabling Keycloak user: " + email, e);
            throw e;
        }
    }

    public Response disableKeycloakUser(String email) {
        LOG.info("Disabling user {}...", email);
        try {
            UserRepresentation userRepresentation = getUserByEmail_keycloak(email);
            Objects.requireNonNull(userRepresentation);
            userRepresentation.setEnabled(false);
            getRealm().users().get(userRepresentation.getId()).update(userRepresentation);
            return Response.ok(getUserByEmail_keycloak(email)).build();
        } catch (Exception e) {
            LOG.error("Error enabling Keycloak user: " + email, e);
            throw e;
        }
    }

    public Response createFhirPractitioner(User user) {
        LOG.info("Creating FHIR practitioner {}...", user.getEmail());

        try {
            String practitionerId = practitionerController.create(User.toPractitioner(user).setActive(true)).getIdPart();
            Objects.requireNonNull(practitionerId);
            return Response.ok(practitionerController.encodeResourceToString(practitionerController.read(practitionerId))).build();
        } catch (NullPointerException e) {
            LOG.error("Error creating FHIR practitioner: " + user.getEmail(), e);
            throw e;
        }
    }

    public Response updateKeycloakUser(User user) {
        UserResource userResource = getRealm().users().get(user.getId());

        // Attempt to find the user by email
        try {
            userResource.update(User.toUserRepresentation(user));
            LOG.info("User updated successfully.");
            return Response.ok(user).build();
        } catch (Exception e) {
            LOG.error("Error retrieving KEYCLOAK user: " + user.getEmail(), e);
            throw e;
        }
    }


    public Response updateFhirPractitioner(User user) {
        // Updating Fhir Practitioner Resource
        try {
            String practitioner = getUserByEmail_fhir(user.getEmail()).getIdPart();
            Objects.requireNonNull(practitioner);
            String practitionerUpdated = practitionerController.encodeResourceToString(practitionerController.update(practitioner, User.toPractitioner(user)));
            LOG.info("Practitioner updated: {}", practitionerUpdated);
            return Response.ok(practitionerUpdated).status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            LOG.error("Error retrieving FHIR practitioner: " + user.getEmail(), e);
            throw e;
        }
    }

    public Response deleteKeycloakUser(String id) {
        try {
            UserResource userResource = getRealm().users().get(id);
            userResource.remove();
            return Response.ok().build();
        } catch (Exception e) {
            LOG.error("ERROR: Couldn't remove Keycloak User: {}.", id, e);
            throw e;
        }
    }

    public void deleteFhirPractitioner(String email) {
        try {
            Practitioner practitioner = getUserByEmail_fhir(email);
            Objects.requireNonNull(practitioner);
            practitionerController.delete(practitioner.getIdPart());
        } catch (Exception e) {
            LOG.error("ERROR: Couldn't remove FHIR Practitioner: {}.", email, e);
            throw e;
        }
    }

    public void joinGroup(String email, GroupRepresentation group) {
        getRealm().users().get(getUserByEmail_keycloak(email).getId()).joinGroup(group.getId());
    }

    public void leaveGroup(String email, GroupRepresentation group) {
        System.out.println(group.getName());
        getRealm().users().get(getUserByEmail_keycloak(email).getId()).leaveGroup(group.getId());
    }

    public UserRepresentation getUserByEmail_keycloak(String email) throws OperationException {
        try {
            return getRealm().users().search(email).get(0);
        } catch (Exception e) {
            LOG.error("ERROR: Couldn't find Keycloak user: {}", email);
            e.printStackTrace();
            throw new OperationException("Couldn't find Keycloak user", OperationOutcome.IssueSeverity.ERROR);
        }
    }

    public Practitioner getUserByEmail_fhir(String email) throws OperationException {
        try {
            return (Practitioner) practitionerController.search("email=" + email).getEntry().get(0).getResource();
        } catch (Exception e) {
            LOG.info(practitionerController.encodeResourceToString(practitionerController.search("email=" + email).getEntry().get(0).getResource()));
            LOG.error("ERROR: Couldn't find FHIR practitioner: {}", email);
            e.printStackTrace();
            throw new OperationException("Couldn't find FHIR practitioner", OperationOutcome.IssueSeverity.ERROR);
        }
    }



    public Response forgotPassword(HashMap<String,String> payload) {
        //verifico se è abilitato, se è abilitato chiamo api di reset psw by email
        //PUT /admin/realms/{realm}/users/{id}/reset-password-email  risponde 200
        //in query param posso mettere il redirect_url se serve
        LOG.info("Forgot PSW user {}...", payload);

        // Ottieni la risorsa degli utenti
        UsersResource usersResource = getRealm().users();

        // Trova l'utente di cui si vuole recuperare la password
        String username = payload.get("username");

        System.out.println("payload:"+payload.toString());
        System.out.println("L'utente:"+username);
        List<UserRepresentation> users = usersResource.search(username);

        if (users.isEmpty()) {
            System.out.println("L'utente non è stato trovato.");
            return Response.status(RestResponse.Status.NOT_FOUND).build();
        }


        System.out.println("L'utente è stato trovato. userid:"+users.get(0).getId());
        try {
            // Ottieni l'ID dell'utente
            String userId = users.get(0).getId();
            // Esegui l'azione di reset della password
            usersResource.get(userId).executeActionsEmail(Arrays.asList("UPDATE_PASSWORD"));

        } catch (Exception e) {
            LOG.error("ERROR: Couldn't send reset psw Keycloak User: {}.", payload.get("username"), e);
            return Response.serverError().build();
        }
        return Response.ok().build();

    }

    public Response updatePassword(HashMap<String,String> payload) {
        //questo meoto d viene chiamato solo dall'interno della pagina personale di profilo
        //PUT /admin/realms/{realm}/users/{id}/reset-password   risponde 204
        LOG.info("Update PSW user {}...", payload);
        // Ottieni la risorsa degli utenti
        UsersResource usersResource = getRealm().users();

        // Trova l'utente di cui si vuole recuperare la password
        String username = payload.get("username");
        List<UserRepresentation> users = usersResource.search(username);

        if (users.isEmpty()) {
            System.out.println("L'utente non è stato trovato.");
            return Response.status(RestResponse.Status.NOT_FOUND).build();
        }


        try {
            // Ottieni l'ID dell'utente
            String userId = users.get(0).getId();

            // Esegui l'azione di reset della password
            //usersResource.get(userId).executeActionsEmail(Arrays.asList("UPDATE_PASSWORD"));
            CredentialRepresentation cRep = new CredentialRepresentation();
            cRep.setType(CredentialRepresentation.PASSWORD);
            cRep.setValue(payload.get("new_password"));
            cRep.setTemporary(false);
            usersResource.get(userId).resetPassword(cRep);

        } catch (Exception e) {
            LOG.error("ERROR: Couldn't send reset psw Keycloak User: {}.", payload.get("username"), e);
            return Response.serverError().build();
        }
        return Response.ok().build();

    }

    public String signUp(String user){
        Response token = keycloakService.getAdminToken();
        if(token.hasEntity()){
            String jwtToken = "Bearer " + token.readEntity(AccessTokenResponse.class).getToken();
            //verifico che l'email non sia già registrata su fhir
            IBaseResource resource = practitionerController.parseResource(Practitioner.class, user);
            Practitioner practitioner = (Practitioner) resource;
            String practitionerEmail =  practitioner.getTelecom().stream().filter(x -> x.getSystem().equals(ContactPoint.ContactPointSystem.EMAIL)).toList().get(0).getValue();
            String result = practitionerClient.practitionerExist(jwtToken, practitionerEmail);
            Bundle bundle = practitionerController.parseResource(Bundle.class, result);
            if(bundle.getTotal() > 0){
                 throw new DataFormatException("Email già in uso");//TODO da internazionalizzare
            }
            return practitionerClient.createUser(jwtToken, user);
        }
        return null;

    }

    public String me(@Context SecurityContext ctx){

        String email = ctx.getUserPrincipal().getName();
        if(null != email){
            Response token = keycloakService.getAdminToken();
            if(token.hasEntity()) {
                String jwtToken = "Bearer " + token.readEntity(AccessTokenResponse.class).getToken();
                return practitionerClient.getCurrentPractitioner(jwtToken, email);
            }
        }
        return Response
                .status(jakarta.ws.rs.core.Response.Status.UNAUTHORIZED.getStatusCode(),jakarta.ws.rs.core.Response.Status.UNAUTHORIZED.getReasonPhrase())
                .build().toString();
    }

}

