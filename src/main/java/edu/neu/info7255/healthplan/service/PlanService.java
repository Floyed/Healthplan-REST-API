package edu.neu.info7255.healthplan.service;

import edu.neu.info7255.healthplan.controller.SchemaController;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service @Slf4j
public class PlanService {

//    @Autowired
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

    public JSONObject getPlanFromKey(String key){

        String plan = jedisService.getPlan(key);
        JSONObject ret = new JSONObject(plan);

        return ret;
    }

    public void deletePlan(String planId){

        try{

            Set<String> keys = jedisService.getListFromPattern("*"+planId+"*");

            for (String key : keys) {
                jedisService.deleteFromRedis(key);
            }
        } catch(Exception e){
            e.getStackTrace();
            log.error(e.getMessage());
            throw e;
        }
    }
}
