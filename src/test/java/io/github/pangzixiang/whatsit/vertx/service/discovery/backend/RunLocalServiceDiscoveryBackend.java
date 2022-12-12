package io.github.pangzixiang.whatsit.vertx.service.discovery.backend;

import io.github.pangzixiang.whatsit.vertx.core.ApplicationRunner;
import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;

public class RunLocalServiceDiscoveryBackend {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ApplicationContext();
        applicationContext.registerWebSocketController(WhatsitServiceDiscoveryBackendWebsocketController.class);
        applicationContext.registerController(WhatsitServiceDiscoveryWebUIController.class);
        ApplicationRunner applicationRunner = new ApplicationRunner(applicationContext);
        applicationRunner.run();
    }
}
