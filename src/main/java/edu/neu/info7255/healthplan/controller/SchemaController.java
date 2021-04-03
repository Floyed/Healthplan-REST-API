package edu.neu.info7255.healthplan.controller;

import edu.neu.info7255.healthplan.service.JedisService;
import edu.neu.info7255.healthplan.validator.JSONSchemaValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController  @Slf4j
public class SchemaController {

    @Autowired
    private JSONSchemaValidator jsonValidator;

    @Autowired
    private JedisService jedisService;

    @GetMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getCurrentSchema(@RequestHeader HttpHeaders headers){

        try {
            //TODO validate

            log.info(headers.toString());

            String schema = jedisService.getSchema();

            if(schema == null) return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

            Map<String, Object> ret = new HashMap<String, Object>();
            ret.put("schema",schema);

            return ResponseEntity.ok().body(ret);
        } catch (Exception e){

            e.printStackTrace();

            log.error("Exception occurred: "+ e.getStackTrace());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }


    @PostMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createSchema(@RequestBody(required = true) String schema,
                                                         @RequestHeader HttpHeaders headers){
        try {
            //TODO validate

            log.info(schema + "----" + headers.toString());

            boolean status = jedisService.insertSchema(schema);

            if(status == false) return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e){

            e.printStackTrace();

            log.error("Exception occurred: "+ e.getStackTrace());

            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

}
