package edu.neu.info7255.healthplan.controller;

import edu.neu.info7255.healthplan.service.JedisService;
import edu.neu.info7255.healthplan.service.PlanService;
import edu.neu.info7255.healthplan.validator.JSONSchemaValidator;
import lombok.extern.slf4j.Slf4j;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController @Slf4j
public class PlanController {

    @Autowired
    private JSONSchemaValidator jsonValidator;

    @Autowired
    private JedisService jedisService;

    @Autowired
    private PlanService planService;

    @GetMapping(value = "/plan/{planId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getPlan(@PathVariable(name = "planId", required = true) String planId,
                                                       @RequestHeader HttpHeaders headers) {
        try{

            if (planId == null || planId.equals("")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", "Body is Empty.Kindly provide the JSON").toString());
            }

            String key = "plan" + jedisService.SEPARATOR + planId;

            if (!jedisService.checkIfKeyExist(key)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new JSONObject().put("Message", "Plan does not exist").toString());
            }

            String actualEtag = key + jedisService.SEPARATOR +"eTag";
            String storedETag = jedisService.getPlan(actualEtag);

            String eTag = headers.getFirst("If-None-Match");
            if (eTag != null && eTag.equals(storedETag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(actualEtag).build();
            }

            JSONObject plan = new JSONObject(jedisService.getPlan(key));

            return ResponseEntity.ok().body(plan.toString());

        } catch(Exception e){
            e.printStackTrace();
            log.error("Exception occurred", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", e.getMessage()).toString());
        }
    }


    @PostMapping(path = "/plan", produces = "application/json")
    public ResponseEntity<Object> createPlan(@RequestBody(required = true) String medicalPlan,
                                             @RequestHeader HttpHeaders headers) throws JSONException, Exception {
        try {

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
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan already exist").toString());
            }

            String eTag = jedisService.savePlanAndGetETag(key, json);

            return ResponseEntity.ok().eTag(eTag).body(" {\"message\": \"Created data with key: " + json.get("objectId") + "\" }");

        } catch (Exception e) {
            e.printStackTrace();
            log.error("Exception occurred", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", e.getMessage()).toString());
        }


    }


    @DeleteMapping(path = "/plan/{planId}", produces = "application/json")
    public ResponseEntity<Object> deletePlan(@PathVariable(required = true) String planId,
                                             @RequestHeader HttpHeaders headers) throws JSONException, Exception {
        try {

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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", ex.getMessage()).toString());
        }
    }
}
