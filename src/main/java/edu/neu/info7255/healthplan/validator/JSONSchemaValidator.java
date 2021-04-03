package edu.neu.info7255.healthplan.validator;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class JSONSchemaValidator {


    public void validateJson(JSONObject object) throws IOException, ValidationException {
        try(InputStream inputStream = getClass().getResourceAsStream("/schema.json")){
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(object);
        }
    }


}
