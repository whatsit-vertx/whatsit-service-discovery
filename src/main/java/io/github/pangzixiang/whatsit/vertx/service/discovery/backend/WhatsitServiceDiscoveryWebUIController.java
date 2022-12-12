package io.github.pangzixiang.whatsit.vertx.service.discovery.backend;

import io.github.pangzixiang.whatsit.vertx.core.annotation.RestController;
import io.github.pangzixiang.whatsit.vertx.core.constant.HttpRequestMethod;
import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;
import io.github.pangzixiang.whatsit.vertx.core.controller.BaseController;
import io.github.pangzixiang.whatsit.vertx.service.discovery.util.JWTUtils;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WhatsitServiceDiscoveryWebUIController extends BaseController {

    /**
     * Instantiates a new Base controller.
     *
     * @param applicationContext the application context
     */
    public WhatsitServiceDiscoveryWebUIController(ApplicationContext applicationContext) {
        super(applicationContext);
    }

    @RestController(path = "/*", method = HttpRequestMethod.GET)
    public StaticHandler staticFile() {
        return StaticHandler.create("META-INF/resources/webjar");
    }

    @RestController(path = "/", method = HttpRequestMethod.GET)
    public void index(RoutingContext routingContext) {
        routingContext.response().addCookie(Cookie.cookie("Token",
                JWTUtils.generateToken(getApplicationContext().getVertx(), getApplicationContext(), true)));
        routingContext.next();
    }
}
