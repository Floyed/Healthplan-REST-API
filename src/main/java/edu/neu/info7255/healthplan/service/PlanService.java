package edu.neu.info7255.healthplan.service;

import edu.neu.info7255.healthplan.controller.SchemaController;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Service @Slf4j
public class PlanService {

    @Autowired
    private final JedisService jedisService;

    public PlanService(){
         jedisService = new JedisService();
    }

    public JedisService getJedisService(){
        return jedisService;
    }

    @Autowired
    private SchemaController schemaController;

    public static void main(String[] args) {
    }

    public String getETag(String planObject, String key) {
        String newEtag = DigestUtils.md5Hex(planObject);
        jedisService.hSet(key, "eTag", newEtag);
        return newEtag;
    }

    public JSONObject getPlanFromKey(String key){

        String plan = jedisService.getValueFromKey(key);
        JSONObject ret = new JSONObject(plan);

        return ret;
    }

    public void deletePlanAndLinkedResources(String planId){

        try{

            Set<String> keys = jedisService.getKeysFromPattern("*"+planId+"*");

            for (String key : keys) {
                jedisService.deleteFromRedis(key);
            }
        } catch(Exception e){
            e.getStackTrace();
            log.error(e.getMessage());
            throw e;
        }
    }

    public String patchPlanAndLinkedResources(JSONObject modifiedValues, String keyWithPlanId, String planId){

        try{

            JSONObject storedPlan = new JSONObject(jedisService.getValueFromKey(keyWithPlanId));

            Iterator<String> iterator = modifiedValues.keySet().iterator();

            while (iterator.hasNext()) {

                String newkey = iterator.next();
                Object newValue = modifiedValues.get(newkey);

                if (newValue instanceof JSONObject) {

                    storedPlan.put(newkey, newValue);

                } else if (newValue instanceof JSONArray) {

                    Object storedValueForArrayKey = storedPlan.get(newkey);

                    if(storedValueForArrayKey instanceof JSONArray){

                        matchAndModifyStoredArray((JSONArray) storedValueForArrayKey,(JSONArray) newValue);
                    } else {
                        storedPlan.put(newkey, newValue);
                    }
                } else {
                    storedPlan.put(newkey, newValue);
                }
            }


            this.deletePlanAndLinkedResources(planId);

            JSONObject resultPlan = storedPlan;

            String key = resultPlan.get("objectType") + jedisService.SEPARATOR + resultPlan.get("objectId");

            return jedisService.savePlanAndGetETag(key, storedPlan);

        } catch(Exception e){
            e.getStackTrace();
            log.error(e.getMessage());
            throw e;
        }
    }


    public boolean matchAndModifyStoredArray(JSONArray storedArray, JSONArray newArray) {

        HashMap<String, Object> map = new HashMap<String, Object>();

        Iterator<Object> iterator = storedArray.iterator();

        while(iterator.hasNext()){

            Object currentStoredElement = iterator.next();

            if(currentStoredElement instanceof JSONObject){

                if(((JSONObject) currentStoredElement).get("objectId") == null
                        || !(((JSONObject) currentStoredElement).get("objectId") instanceof String)
                        || ((JSONObject) currentStoredElement).get("objectId").equals("")) {
                    return false;
                }

                String currentStoredElementId = (String) ((JSONObject) currentStoredElement).get("objectId");

                map.put(currentStoredElementId, currentStoredElement);

            }
        }



        iterator = newArray.iterator();

        while(iterator.hasNext()){

            Object toAddElement = iterator.next();

            if(toAddElement instanceof JSONObject){

                if(((JSONObject) toAddElement).get("objectId") == null
                        || !(((JSONObject) toAddElement).get("objectId") instanceof String)
                        || ((JSONObject) toAddElement).get("objectId").equals("")) {
                    return false;
                }

                String currentToAddElementId = (String) ((JSONObject) toAddElement).get("objectId");

                map.put(currentToAddElementId, toAddElement);

            }
        }

        while(!storedArray.isEmpty()){
            storedArray.remove(0);
        }

        for(Map.Entry<String, Object> entry : map.entrySet()){
            storedArray.put(entry.getValue());
        }

        return true;

    }

}
