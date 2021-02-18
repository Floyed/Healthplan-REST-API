package edu.neu.info7255.healthplan.service;

import lombok.extern.slf4j.Slf4j;
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

    public String getPlan(String key) {

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


    public void savePlan(String pre, JSONObject object){

        saveToRedis(pre, String.valueOf(object));

        saveMapfromJSONObject(pre, object);
    }

    public Map<String, Object> saveMapfromJSONObject(String pre, JSONObject object) {

        try {

            String objectId = object.getString("objectId");
            String objectType = object.getString("objectType");
            Map<String, Object> ret = new HashMap<String, Object>();

            Iterator<String> iterator = object.keySet().iterator();

            while (iterator.hasNext()) {

                String key = iterator.next();
                Object value = object.get(key);

                 String newKey = pre + SEPARATOR + key;


                if (value instanceof JSONObject) {

                    Map<String, Object> child = saveMapfromJSONObject(newKey, (JSONObject) value);

                    ret.put(newKey, (Map<String, Object>)child);

                } else if (value instanceof JSONArray) {

                    List<Object> child = saveMapfromJSONList(newKey, (JSONArray) value);

                    ret.put(newKey, child);

                } else {
                    saveToRedis(newKey, String.valueOf(value));
                    ret.put(newKey, value);

                }
             }

            return ret;

        } catch (JedisException e) {
            log.error(e.getMessage());
            throw e;
        }
    }


    private List<Object> saveMapfromJSONList(String pre, JSONArray array) {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = saveMapfromJSONList(pre, (JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = saveMapfromJSONObject(pre, (JSONObject) value);
            }
            list.add(value);
        }
        return list;
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

            for(String key : this.getListFromPattern(pattern)){
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

    public Set<String> getListFromPattern(String pattern) {
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
