package io.github.pangzixiang.whatsit.vertx.service.discovery.util;

import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class JWTUtils {

    private static JWTAuth jwtAuth;

    private JWTUtils() {
    }

    public static JWTAuth newInstance(Vertx vertx) {
        if (jwtAuth == null) {
            jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                    .addPubSecKey(new PubSecKeyOptions()
                            .setAlgorithm("HS256")
                            .setBuffer(UUID.randomUUID().toString())));
        }
        return jwtAuth;
    }

    public static String generateToken(Vertx vertx, ApplicationContext applicationContext, boolean ui, JsonObject jsonObject) {
        Objects.requireNonNull(jsonObject);
        JWTAuth auth = newInstance(vertx);
        if (ui) {
            return auth.generateToken(
                    jsonObject,
                    new JWTOptions()
                            .setExpiresInSeconds(10)
                            .setIssuer(applicationContext.getApplicationConfiguration().getName())
                            .setSubject("UI-TOKEN")
                            .setAudience(List.of("UI"))
            );
        } else {
            return auth.generateToken(
                    jsonObject,
                    new JWTOptions()
                            .setExpiresInMinutes(180)
                            .setIssuer(applicationContext.getApplicationConfiguration().getName())
                            .setSubject("SERVER-TOKEN")
                            .setAudience(List.of("SERVICE"))
            );
        }
    }

    public static String generateToken(Vertx vertx, ApplicationContext applicationContext, boolean ui) {
        return generateToken(vertx, applicationContext, ui, new JsonObject());
    }

    public static Future<Boolean> validateToken(Vertx vertx, boolean ui, String token) {
        if (StringUtils.isBlank(token)) {
            log.error("Token Validation Failed due to blank token!");
            return Future.succeededFuture(false);
        }
        JWTAuth auth = newInstance(vertx);
        JsonObject jsonObject = new JsonObject()
                .put("token", token);
        if (ui) {
            jsonObject.put("audience", "UI");
        } else {
            jsonObject.put("audience", "SERVICE");
        }
        return auth.authenticate(jsonObject)
                .compose(user -> {
                    log.info("Token validation Passed! ({})", user.subject());
                    return Future.succeededFuture(true);
                }, throwable -> {
                    log.error("Token validation Failed!", throwable);
                    return Future.succeededFuture(false);
                });
    }
}
