package edu.neu.info7255.healthplan.controller;

import edu.neu.info7255.healthplan.service.JedisService;
import edu.neu.info7255.healthplan.service.PlanService;
import edu.neu.info7255.healthplan.validator.JSONSchemaValidator;
import lombok.extern.slf4j.Slf4j;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import org.springframework.web.client.RestTemplate;

@RestController @Slf4j
public class PlanController {

    @Autowired
    private JSONSchemaValidator jsonValidator;

    @Autowired
    private JedisService jedisService;

    @Autowired
    private PlanService planService;

    private RSAKey rsaPublicJWK;

    boolean checkToken = true;

    public PlanController() throws JOSEException {

        RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();

        rsaPublicJWK = rsaJWK.toPublicJWK();
    }


    @GetMapping(value = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getObject(@PathVariable(name = "objectId", required = true) String objectId,
                                          @PathVariable(name = "objectType", required = true) String objectType,
                                                       @RequestHeader HttpHeaders headers) {
        try{

            if (checkToken && !ifAuthorized(headers)) {
                String res = "{\"status\": \"Failed\",\"message\": \"Unauthorized\"}";
                return ResponseEntity.badRequest().body(res);
            }

            if (objectId == null || objectId.equals("") || objectType == null || objectType.equals("")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", "PlanId & objectType required").toString());
            }

            String key = objectType + jedisService.SEPARATOR + objectId;

            if (jedisService.getKeysFromPattern("*"+key).size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("Message", "Object does not exist").toString());
            }

            String eTagKey = key + jedisService.SEPARATOR +"eTag";

            String storedETagValue = jedisService.getValueFromPattern("*"+eTagKey);
            String requestETagValue = headers.getFirst("If-None-Match");

            if (requestETagValue != null && requestETagValue.equals(storedETagValue)) {
                //etag value is the same as stored
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(storedETagValue).build();
            }

            JSONObject object = new JSONObject(jedisService.getValueFromPattern("*"+key));

            return ResponseEntity.ok().eTag(storedETagValue).body(object.toString());

        } catch(Exception e){
            e.printStackTrace();
            log.error("Exception occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new JSONObject().put("Error", e.getMessage()).toString());
        }
    }


    @PostMapping(path = "/plan", produces = "application/json")
    public ResponseEntity<Object> createPlan(@RequestBody(required = true) String medicalPlan,
                                             @RequestHeader HttpHeaders headers) throws JSONException, Exception {
        try {

            if (checkToken && !ifAuthorized(headers)) {
                String res = "{\"status\": \"Failed\",\"message\": \"Unauthorized\"}";
                return ResponseEntity.badRequest().body(res);
            }

            if (medicalPlan == null || medicalPlan.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", "Body is Empty.Kindly provide the JSON").toString());
            }

            JSONObject json = new JSONObject(medicalPlan);
            try {
                jsonValidator.validateJson(json);
            } catch (ValidationException | IOException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", ex.getMessage()).toString());
            }

            String key = json.get("objectType").toString() + jedisService.SEPARATOR + json.get("objectId").toString();

            if (jedisService.checkIfKeyExist(key)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan already exists").toString());
            }

            String eTag = jedisService.savePlanAndGetETag(key, json);

            return ResponseEntity.status(HttpStatus.CREATED).eTag(eTag).body(" {\"message\": \"Created plan with id: " + json.get("objectId") + "\" }");

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new JSONObject().put("Error", e.getMessage()).toString());
        }


    }


    @DeleteMapping(path = "/plan/{planId}", produces = "application/json")
    public ResponseEntity<Object> deletePlan(@PathVariable(required = true) String planId,
                                             @RequestHeader HttpHeaders headers) throws JSONException, Exception {
        try {

            if (checkToken && ifAuthorized(headers)) {
                String res = "{\"status\": \"Failed\",\"message\": \"Unauthorized\"}";
                return ResponseEntity.badRequest().body(res);
            }

            if(planId == null || planId.equals("")){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", "Body is Empty.Kindly provide the JSON").toString());
            }

            String key = "plan" + jedisService.SEPARATOR + planId;

            if (!jedisService.checkIfKeyExist(key)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan does not exist").toString());
            }

            planService.deletePlanAndLinkedResources(planId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new JSONObject().put("Error", ex.getMessage()).toString());
        }
    }


    @PutMapping({"/plan", "/plan/{planId}"})
    public ResponseEntity<String> updatePlan(@PathVariable(required = false) String planId,
                                             @RequestBody(required = true) String medicalPlan,
                                             @RequestHeader HttpHeaders headers) {

        try {

            if (checkToken && !ifAuthorized(headers)) {
                String res = "{\"status\": \"Failed\",\"message\": \"Unauthorized\"}";
                return ResponseEntity.badRequest().body(res);
            }

            JSONObject json = new JSONObject(medicalPlan);
            try {
                jsonValidator.validateJson(json);
            } catch (ValidationException | IOException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", ex.getMessage()).toString());
            }

            // No id mentioned (url and body)
            String key = json.get("objectType") + jedisService.SEPARATOR + json.get("objectId");

            String eTagKey = key + jedisService.SEPARATOR +"eTag";

            if (!headers.containsKey("If-Match")) {
                //There is no etag
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Message", "There is no ETag value in request").toString());
            }

            String storedETagValue = jedisService.getValueFromPattern("*"+eTagKey);
            String requestETagValue = headers.getFirst("If-Match");

            if (requestETagValue != null && !requestETagValue.equals(storedETagValue)) {
                //etag value is the different from server. Therefore, not returning etag
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(new JSONObject().put("Message", "ETag value has changed").toString());
            }

            String targetId = json.get("objectId").toString();
            HttpStatus ret = null;

            // If object exists return 200 OK or 204 No content
            if (jedisService.checkIfKeyExist(key)) {

                // Delete it first
                planService.deletePlanAndLinkedResources(targetId);

                ret = HttpStatus.OK;
            }
            // Else create it and return 201 created
            else {
                ret = HttpStatus.CREATED;
            }

            String eTag = jedisService.savePlanAndGetETag(key, json);

            return ResponseEntity.status(ret).eTag(eTag).body(new JSONObject().put("message", "PUT operation for id: " + json.get("objectId") + " is successful").toString());

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new JSONObject().put("Error", e.getMessage()).toString());
        }

    }

    @PatchMapping("/plan/{urlPlanId}")
    public ResponseEntity<String> patchPlan(@PathVariable(required = true) String urlPlanId,
                                             @RequestBody(required = true) String modifiedContent,
                                             @RequestHeader HttpHeaders headers) {

        try {

            if (checkToken && !ifAuthorized(headers)) {
                String res = "{\"status\": \"Failed\",\"message\": \"Unauthorized\"}";
                return ResponseEntity.badRequest().body(res);
            }

            JSONObject inputJson = null;

            try{
                inputJson = new JSONObject(modifiedContent);
            } catch(JSONException je){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", je.getMessage()).toString());
            }

            // Id mentioned (url and body)
            if (inputJson.has("objectId")) {

                Object bodyPlanId = inputJson.get("objectId");

                //Not allowing ID to be updated
                if(!(bodyPlanId instanceof String) || ((String)bodyPlanId).equals("") || !((String)bodyPlanId).equals(urlPlanId)){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", "Kindly provide the correct body for PATCH Operation").toString());
                }
            }

            String key = "plan" + jedisService.SEPARATOR + urlPlanId;

            if (!jedisService.checkIfKeyExist(key)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan does not exist").toString());
            }

            // No id mentioned (url and body)
            String eTagKey = key + jedisService.SEPARATOR +"eTag";

            if (!headers.containsKey("If-Match")) {
                //There is no etag
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Message", "There is no ETag value in request").toString());
            }

            String storedETagValue = jedisService.getValueFromPattern("*"+eTagKey);
            String requestETagValue = headers.getFirst("If-Match");

            if (requestETagValue != null && !requestETagValue.equals(storedETagValue)) {
                //etag value is the different from server. Therefore, not returning etag
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(new JSONObject().put("Message", "ETag value has changed").toString());
            }

            String eTag = planService.patchPlanAndLinkedResources(inputJson, key, urlPlanId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(eTag).body(new JSONObject().put("message", "PATCH operation for id: " + urlPlanId + " is successful").toString());

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new JSONObject().put("Error", e.getMessage()).toString());
        }
    }


    @GetMapping("/token")
    public ResponseEntity<String> getToken() throws JOSEException, ParseException {

        try{


            // RSA signatures require a public and private RSA key pair, the public key
            // must be made known to the JWS recipient in order to verify the signatures
            RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
            rsaPublicJWK = rsaJWK.toPublicJWK();
            // verifier = new RSASSAVerifier(rsaPublicJWK);

            // Create RSA-signer with the private key
            JWSSigner signer = new RSASSASigner(rsaJWK);

            // Prepare JWT with claims set
            int expireTime = 30000; // seconds

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .expirationTime(new Date(new Date().getTime() + expireTime * 1000)) // milliseconds
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJWK.getKeyID()).build(),
                    claimsSet);

            // Compute the RSA signature
            signedJWT.sign(signer);

            // To serialize to compact form, produces something like
            // eyJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.IRMQENi4nJyp4er2L
            // mZq3ivwoAjqa1uUkSBKFIX7ATndFF5ivnt-m8uApHO4kfIFOrW7w2Ezmlg3Qd
            // maXlS9DhN0nUk_hGI3amEjkKd0BWYCB8vfUbUv0XGjQip78AI4z1PrFRNidm7
            // -jPDm5Iq0SZnjKjCNS5Q15fokXZc8u0A
            String token = signedJWT.serialize();

            String res = "{\"status\": \"Successful\",\"token\": \"" + token + "\"}";
            return new ResponseEntity<String>(res, HttpStatus.OK);




        } catch(Exception ex){

            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new JSONObject().put("Error", ex.getMessage()).toString());

        }


    }

    private boolean ifAuthorized(HttpHeaders requestHeaders) throws ParseException, JOSEException {

        String auth = requestHeaders.getFirst("Authorization");

        if(auth == null || auth.equals("")){
            return false;
        }

        String token = requestHeaders.getFirst("Authorization").substring(7);
        // On the consumer side, parse the JWS and verify its RSA signature
        SignedJWT signedJWT = SignedJWT.parse(token);

        JWSVerifier verifier = new RSASSAVerifier(rsaPublicJWK);
        // Retrieve / verify the JWT claims according to the app requirements
        if (!signedJWT.verify(verifier)) {
            return false;
        }
        JWTClaimsSet claimset = signedJWT.getJWTClaimsSet();
        Date exp = 	claimset.getExpirationTime();

        // System.out.println(exp);
        // System.out.println(new Date());

        return new Date().before(exp);
    }
}
