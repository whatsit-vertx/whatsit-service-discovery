package io.github.pangzixiang.whatsit.vertx.service.discovery.backend;

import io.github.pangzixiang.whatsit.vertx.core.annotation.RestController;
import io.github.pangzixiang.whatsit.vertx.core.constant.HttpRequestMethod;
import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;
import io.github.pangzixiang.whatsit.vertx.core.controller.BaseController;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static io.github.pangzixiang.whatsit.vertx.service.discovery.backend.WhatsitServiceDiscoveryBackendWebsocketController.getUiKey;

@Slf4j
public class WhatsitServiceDiscoveryBackendApiController extends BaseController {

    private final ThymeleafTemplateEngine thymeleafTemplateEngine;

    /**
     * Instantiates a new Base controller.
     *
     * @param applicationContext the application context
     */
    public WhatsitServiceDiscoveryBackendApiController(ApplicationContext applicationContext) {
        super(applicationContext);
        this.thymeleafTemplateEngine = ThymeleafTemplateEngine.create(applicationContext.getVertx());
    }

    @RestController(path = "/", method = HttpRequestMethod.GET)
    public void index(RoutingContext routingContext) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", getUiKey());
        thymeleafTemplateEngine.render(params, "web/index.html")
                .onSuccess(buffer -> routingContext.response().send(buffer))
                .onFailure(throwable -> {
                    sendJsonResponse(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, throwable.getMessage());
                });
    }

    @RestController(path = "/*", method = HttpRequestMethod.GET)
    public StaticHandler staticFile() {
        return StaticHandler.create("web");
    }
}
