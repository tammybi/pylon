package com.yotouch.base.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yotouch.core.Consts;
import com.yotouch.core.entity.Entity;
import com.yotouch.core.runtime.DbSession;
import com.yotouch.core.runtime.YotouchApplication;
import com.yotouch.core.runtime.YotouchRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    
    static final private Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    
    @Autowired
    private YotouchApplication ytApp;

    @Autowired
    private RoleService roleService;

    @Override
    public String genPassword(Entity user, String password) {
        String instanceName = ytApp.getInstanceName();
        logger.info("Instance name " + instanceName);
        String pwdStr = "yotouch:" + instanceName + ":" + user.getUuid() + ":" + password;
        logger.info("Gen plain password " + pwdStr);
        String md5Pwd = DigestUtils.md5DigestAsHex(pwdStr.getBytes());
        return md5Pwd;
    }

    @Override
    public String genLoginToken(Entity user) {
        
        String token = "1." + user.getMetaEntity().getName() + ".";
        
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("uuid", user.getUuid());
        
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            String dataStr = mapper.writeValueAsString(dataMap);
            logger.info("Token data string " + dataStr);
            long now = System.currentTimeMillis() / 1000; // 单位到秒

            String dataInfo = Base64Utils.encodeToString(dataStr.getBytes());
            token = token + dataInfo + "." + now;
            
            String vcode = DigestUtils.md5DigestAsHex(token.getBytes()).substring(8, 16);
            token += "." + vcode;

            logger.info(" token " + token);

            return token;
            
        } catch (JsonProcessingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public Entity checkLoginUser(String userToken) {
        
        if (StringUtils.isEmpty(userToken)) {
            return null;
        }
        
        // "1.user.eyJ1dWlkIjoiYjc5MDQzYzEtZTk1ZC00ZjY0LWI1ZGMtYWUyYTI1Y2M0NGY0In0=.1455088972.a4e765cd"   

        String[] parts = userToken.split("\\.");
        if (parts.length == 5) {
            String formatVersion = parts[0];
            String type          = parts[1];
            String infoStr       = parts[2];
            String genTime       = parts[3];
            String vcode         = parts[4];

            if ("1".equals(formatVersion)) {
                
                String otherVcode = DigestUtils.md5DigestAsHex((formatVersion + "." + type + "." + infoStr + "." + genTime).getBytes()).substring(8, 16);
                if (!otherVcode.equals(vcode)) {
                    return null;
                }
                
                infoStr = new String(Base64Utils.decodeFromString(infoStr));
                logger.info("info str " + infoStr);
                
                ObjectMapper mapper = new ObjectMapper();
                try {
                    Map<String, String> map = mapper.readValue(infoStr, new TypeReference<Map<String, String>>() {});
                    String uuid = map.get("uuid");
                    YotouchRuntime runtime = ytApp.getRuntime();
                    DbSession dbSession = runtime.createDbSession();
                    Entity user = dbSession.getEntity("user", uuid);
                    return user;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return null;
    }

    @Override
    public Entity modifyPassword(Entity user, String password, String newPassword) {
        YotouchRuntime runtime = ytApp.getRuntime();
        DbSession dbSession = runtime.createDbSession();
        Entity u = dbSession.queryOneRawSql(user.getMetaEntity().getName(), "password = ? AND status = ?", new Object[]{genPassword(user, password), Consts.STATUS_NORMAL});
        if (u == null){
            return null;
        }

        u.setValue("password", genPassword(u, newPassword));

        return dbSession.save(u);
    }

    @Override
    public Entity addDefaultRoleByEnterUrl(DbSession dbSession, Entity user, String enterUrl) {
        if (enterUrl.startsWith("/interview/genpaper/")) {
            Entity userRole = dbSession.newEntity("userRole");
            userRole.setValue("user", user.getUuid());
            userRole.setValue("role", roleService.getOrCreateByName(Consts.ROLE_INTERVIEWEE_NAME));
            userRole.setValue("status", Consts.STATUS_NORMAL);
            dbSession.save(userRole);
        }

        return user;
    }

}
