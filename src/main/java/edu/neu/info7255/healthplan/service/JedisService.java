package edu.neu.info7255.healthplan.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.util.*;

@Service  @Slf4j
public class JedisService {

    private static final String redisHost = "localhost";
    private static final Integer redisPort = 6379;
    private static JedisPool pool = null;
    private static String REDIS_PLAN_SCHEMA_KEY = "schema_health_plan";
    public static final String SEPARATOR = "___";

    public JedisService() {
        pool = new JedisPool(redisHost, redisPort);
    }

    public static void main(String[] args) {
        JedisService jedisService = new JedisService();
        System.out.println(jedisService.insertSchema("abc"));
        System.out.println(jedisService.getSchema());
    }

    public String getSchema() {

        Jedis jedis = null;

        try {
            jedis = pool.getResource();
            return jedis.get(REDIS_PLAN_SCHEMA_KEY);
        } catch(JedisException e) {
            e.printStackTrace();
            return null;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }
    }

    public boolean insertSchema(String schema) {

        Jedis jedis = null;

        try {
            jedis = pool.getResource();
            if(jedis.set(REDIS_PLAN_SCHEMA_KEY, schema).equals("OK"))
                return true;
            else
                return false;
        } catch (JedisException e) {
            e.printStackTrace();
            return false;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }
    }

    public String getValueFromPattern(String pattern) {

        Jedis jedis = null;

        try {
            jedis = pool.getResource();
            Set<String> keys =  jedis.keys(pattern);

            String ret = null;

            for(String s : keys){
                ret = getValueFromKey(s);
                break;
            }

            return ret;

        } catch(JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }
    }

    public String getValueFromKey(String key) {

        Jedis jedis = null;

        try {
            jedis = pool.getResource();
            return jedis.get(key);
        } catch(JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }
    }

    public void saveToRedis(String key, String value){
        Jedis jedis = null;
        try {

            jedis = pool.getResource();
            if(!jedis.set(key, value).equals("OK"))
                throw new JedisException("");
        } catch (JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }


    }

    public void deleteFromRedis(String key){
        Jedis jedis = null;
        try {

            jedis = pool.getResource();
            jedis.del(key);
        } catch (JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }


    }


    public String savePlanAndGetETag(String pre, JSONObject object){

        saveToRedis(pre, String.valueOf(object));

        saveChildren(pre, object);

        String newEtag = DigestUtils.md5Hex(object.toString());
        String eTag_key = pre + SEPARATOR +"eTag";
        saveToRedis(eTag_key, newEtag);

        return newEtag;
    }

    public void saveChildren(String pre, JSONObject object) {

        try {

            String objectId = object.getString("objectId");
            String objectType = object.getString("objectType");

            String oKey = pre + SEPARATOR + objectType + SEPARATOR + objectId;

            saveToRedis(oKey, String.valueOf(object));

            String newEtag = DigestUtils.md5Hex(object.toString());
            String eTag_key = oKey + SEPARATOR +"eTag";
            saveToRedis(eTag_key, newEtag);

            Iterator<String> iterator = object.keySet().iterator();

            while (iterator.hasNext()) {

                String key = iterator.next();
                Object value = object.get(key);

                String newKey = pre + SEPARATOR + key;

                if (value instanceof JSONObject) {

                    JSONObject jsonObj = (JSONObject) value;

                    String id = jsonObj.getString("objectId");
                    String type = jsonObj.getString("objectType");

                    String objectKey = pre + SEPARATOR + type + SEPARATOR + id;
                    saveToRedis(objectKey, String.valueOf(value));

                    newEtag = DigestUtils.md5Hex(object.toString());
                    eTag_key = objectKey + SEPARATOR +"eTag";
                    saveToRedis(eTag_key, newEtag);

                    saveChildren(newKey, (JSONObject) value);
                } else if (value instanceof JSONArray) {
                    saveMapfromJSONList(newKey, (JSONArray) value);
                } else {
                    saveToRedis(newKey, String.valueOf(value));
                }
             }
        } catch (JedisException e) {
            log.error(e.getMessage());
            throw e;
        }
    }


    // Sets the specified hash field to the specified value
    public void hSet(String key, String field, String value ) {

        Jedis jedis = null;
        try {

            jedis = pool.getResource();
            jedis.hset(key, field, value);
        } catch (JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }

    }


    private void saveMapfromJSONList(String pre, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                saveMapfromJSONList(pre, (JSONArray) value);
            } else if (value instanceof JSONObject) {
                saveChildren(pre, (JSONObject) value);
            }
        }
    }

    public boolean checkIfKeyExist(String key) {

        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            return jedis.exists(key);
        } catch(JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }
    }

    public void deletePattern(String pattern) {

        Jedis jedis = null;

        try {
            jedis = pool.getResource();

            for(String key : this.getKeysFromPattern(pattern)){
                jedis.del(key);
            }

        } catch(JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if(jedis != null){
                jedis.close();
            }
        }
    }

    public Set<String> getKeysFromPattern(String pattern) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            return jedis.keys(pattern);
        } catch (JedisException e) {
            e.printStackTrace();
            throw e;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }


}
