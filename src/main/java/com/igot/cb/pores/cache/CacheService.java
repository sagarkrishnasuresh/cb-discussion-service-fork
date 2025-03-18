package com.igot.cb.pores.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.Constants;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Service
@Slf4j
public class CacheService {

  @Autowired
  private JedisPool jedisPool;
  @Autowired
  private ObjectMapper objectMapper;

  @Value("${spring.redis.cacheTtl}")
  private long cacheTtl;

  @Autowired
  private JedisPool jedisDataPopulationPool;

  public Jedis getJedis() {
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis;
    }
  }

  public void putCache(String key, Object object) {
    try {
      String data = objectMapper.writeValueAsString(object);
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.set(Constants.REDIS_KEY_PREFIX + key, data);
        jedis.expire(Constants.REDIS_KEY_PREFIX + key, cacheTtl);
      }
    } catch (Exception e) {
      log.error("Error while putting data in Redis cache: {} ", e.getMessage());
    }
  }

  public String getCache(String key) {
    try {
      return getJedis().get(Constants.REDIS_KEY_PREFIX + key);
    } catch (Exception e) {
      return null;
    }
  }

  public Long deleteCache(String key) {
    try (Jedis jedis = jedisPool.getResource()) {
      Long result = jedis.del(Constants.REDIS_KEY_PREFIX + key);
      if (result == 1) {
        log.info("Field {} deleted successfully from key {}.", key);
      } else {
        log.warn("Field {} not found in key {}.", key);
      }
      return result;
    } catch (Exception e) {
      log.error("Error while deleting data from Redis cache: {} ", e.getMessage());
      return null;
    }
  }
  public List<Object> hget(List<String> keys) {
    List<Object> resultList = new ArrayList<>();
    try (Jedis jedis = jedisDataPopulationPool.getResource()) {
      for (String key : keys) {
        String values = jedis.get(key); // Fetch entire hash
        resultList.add(values); // Add map as an Object
      }
    } catch (Exception e) {
      log.error("Error while fetching data from Redis: {}", e.getMessage(), e);
    }
    return resultList;
  }

}
