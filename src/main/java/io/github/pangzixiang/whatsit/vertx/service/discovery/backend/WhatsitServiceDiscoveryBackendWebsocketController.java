package io.github.pangzixiang.whatsit.vertx.service.discovery.backend;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.pangzixiang.whatsit.vertx.core.annotation.WebSocketAnnotation;
import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;
import io.github.pangzixiang.whatsit.vertx.core.model.HttpResponse;
import io.github.pangzixiang.whatsit.vertx.core.websocket.controller.AbstractWebSocketController;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.types.HttpEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.pangzixiang.whatsit.vertx.core.utils.CoreUtils.objectToString;

@Slf4j
@WebSocketAnnotation(path = "/discovery")
public class WhatsitServiceDiscoveryBackendWebsocketController extends AbstractWebSocketController {

    private final ServiceDiscovery serviceDiscovery;

    private final Cache<ServerWebSocket, Boolean> cache;

    private static final AtomicReference<String> uiKey = new AtomicReference<>(RandomStringUtils.randomAlphanumeric(10));

    public static AtomicReference<String> getUiKey() {
        return uiKey;
    }

    public WhatsitServiceDiscoveryBackendWebsocketController(ApplicationContext applicationContext, Vertx vertx) {
        super(applicationContext, vertx);
        this.serviceDiscovery = ServiceDiscovery.create(vertx,
                new ServiceDiscoveryOptions()
                        .setBackendConfiguration(new JsonObject()
                                .put("backend-name", WhatsitServiceDiscoveryBackend.class.getName())
                                .put("context", applicationContext)
                        )
                        .setName(applicationContext.getApplicationConfiguration().getName()));
        this.cache = (Cache<ServerWebSocket, Boolean>) applicationContext.getCache("WhatsitServiceDiscoveryWebSocketCache");
    }

    @Override
    public void startConnect(ServerWebSocket serverWebSocket) {
        final Map<String, String> queryMap = parseQueryString(serverWebSocket.query());
        if (BooleanUtils.isTrue(Boolean.valueOf(queryMap.get("isUI")))) {
            String key = queryMap.get("key");
            if (key != null && key.equals(getUiKey().getAndSet(RandomStringUtils.randomAlphanumeric(10)))) {
                this.cache.put(serverWebSocket, false);
                sendAllRecords(serverWebSocket);
                log.info("UI Connected! {}", serverWebSocket.binaryHandlerID());
            } else {
                log.warn("Invalid Token [{}] for UI Connection! {}", key, serverWebSocket.binaryHandlerID());
                sendResponse(serverWebSocket, HttpResponseStatus.UNAUTHORIZED, "Invalid Token")
                        .onComplete(c -> serverWebSocket.close((short) 1008));
            }
        } else {
            final String serviceName = queryMap.get("name");
            final String host = queryMap.get("host");
            final String port = queryMap.get("port");
            if (StringUtils.isAnyBlank(serviceName, host, port)) {
                log.warn("Invalid Connection! {} \nheaders: [{}] \nquery: [{}] \nremoteAddr: [{}]"
                        , serverWebSocket.binaryHandlerID(), serverWebSocket.headers(),
                        serverWebSocket.query(), serverWebSocket.remoteAddress());
                sendResponse(serverWebSocket, HttpResponseStatus.BAD_REQUEST, "Invalid Connection!")
                        .onComplete(complete -> serverWebSocket.close((short) 1008));
            } else {
                Record record = HttpEndpoint.createRecord(serviceName, host, Integer.parseInt(port), "/" + serviceName);
                record.setRegistration(serverWebSocket.binaryHandlerID());
                serviceDiscovery.publish(record)
                        .onSuccess(r -> {
                            log.info("Service {} connected! {}", serviceName, serverWebSocket.binaryHandlerID());
                            this.cache.put(serverWebSocket, true);
                            log.info("Record {} registered!", r.toJson().encodePrettily());
                            this.cache.asMap().forEach(((webSocket, isService) -> sendAllRecords(webSocket)));
                        })
                        .onFailure(throwable -> {
                            log.error("Failed to register service {}", serviceName, throwable);
                            sendResponse(serverWebSocket, HttpResponseStatus.BAD_REQUEST, "Failed to register (%s)".formatted(throwable.getMessage()))
                                    .onComplete(complete -> serverWebSocket.close((short) 1008));
                        });
            }
        }
    }

    @Override
    public Handler<WebSocketFrame> onConnect(ServerWebSocket serverWebSocket) {
        return webSocketFrame -> {
            String command = webSocketFrame.textData();
            log.info("received command [{}] from [{}]", command, serverWebSocket.binaryHandlerID());
            if (command.equals("ALL")) {
                sendAllRecords(serverWebSocket);
            } else if (command.equals("DOWN")) {
                updateRecordStatus(Status.DOWN, serverWebSocket);
            } else if (command.equals("UP")) {
                updateRecordStatus(Status.UP, serverWebSocket);
            } else {
                sendResponse(serverWebSocket, HttpResponseStatus.BAD_REQUEST, "Invalid Command (%s)".formatted(command));
            }
        };
    }

    @Override
    public Handler<Void> closeConnect(ServerWebSocket serverWebSocket) {
        return v -> {
            log.info("Connection [{}] Closed!", serverWebSocket.binaryHandlerID());
            if (this.cache.getIfPresent(serverWebSocket) != null) {
                this.serviceDiscovery.unpublish(serverWebSocket.binaryHandlerID())
                        .onComplete(complete -> {
                            this.cache.invalidate(serverWebSocket);
                            this.cache.asMap()
                                    .forEach((webSocket, isService) -> {
                                        sendAllRecords(webSocket);
                                    });
                        });
            }
        };
    }

    private Future<Void> sendAllRecords(ServerWebSocket serverWebSocket) {
        return this.serviceDiscovery.getRecords(new JsonObject())
                .compose(records -> {
                    log.debug("Send result to {}", serverWebSocket.binaryHandlerID());
                    return sendResponse(serverWebSocket, HttpResponseStatus.OK, records);
                }, throwable -> {
                    log.error("Failed to get all records!", throwable);
                    return sendResponse(serverWebSocket,
                            HttpResponseStatus.BAD_REQUEST,
                            "Failed to get records (%s)".formatted(throwable.getMessage()));
                });
    }

    private void updateRecordStatus(Status status, ServerWebSocket serverWebSocket) {
        this.serviceDiscovery.getRecord(serverWebSocket.binaryHandlerID())
                .compose(record -> {
                    if (record != null) {
                        return this.serviceDiscovery.update(record.setStatus(status));
                    } else {
                        return Future.failedFuture("record [%s] NOT FOUND!".formatted(serverWebSocket.binaryHandlerID()));
                    }
                })
                .compose(record -> {
                    this.cache.asMap().forEach((webSocket, isService) -> sendAllRecords(webSocket));
                    return Future.succeededFuture();
                }, throwable -> {
                    log.error("Failed to update [{}] status!", serverWebSocket.binaryHandlerID(), throwable);
                    return sendResponse(serverWebSocket,
                            HttpResponseStatus.BAD_REQUEST,
                            "Failed to update status (%s)".formatted(throwable.getMessage()));
                });
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) {
            return result;
        }
        String[] temp = query.split("&");
        if (temp.length > 0) {
            Arrays.stream(temp).forEach(s -> {
                if (StringUtils.isNoneBlank(s)) {
                    String[] t = s.split("=");
                    if (t.length == 2) {
                        result.put(URLDecoder.decode(t[0], StandardCharsets.UTF_8),
                                URLDecoder.decode(t[1], StandardCharsets.UTF_8));
                    }
                }
            });
        }
        return result;
    }

    private Future<Void> sendResponse(ServerWebSocket serverWebSocket, HttpResponseStatus status, Object data) {
        if (serverWebSocket.isClosed()) {
            log.warn("Connection [{}] already closed! would not send back response!", serverWebSocket.binaryHandlerID());
            return Future.succeededFuture();
        }
        return serverWebSocket.writeTextMessage(objectToString(HttpResponse.builder().status(status).data(data).build()))
                .onSuccess(success -> log.info("Succeed to send message [{}] to [{}]", data, serverWebSocket.binaryHandlerID()))
                .onFailure(throwable -> log.error("Failed to send message [{}] to [{}]", data, serverWebSocket.binaryHandlerID(), throwable));
    }
}
