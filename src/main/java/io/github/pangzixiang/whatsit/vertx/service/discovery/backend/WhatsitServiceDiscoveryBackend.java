package io.github.pangzixiang.whatsit.vertx.service.discovery.backend;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.auto.service.AutoService;
import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.spi.ServiceDiscoveryBackend;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@AutoService(ServiceDiscoveryBackend.class)
public class WhatsitServiceDiscoveryBackend implements ServiceDiscoveryBackend {

    private ApplicationContext applicationContext;

    private Cache<String, Record> cache;

    @Override
    public void init(Vertx vertx, JsonObject jsonObject) {
        log.info("Start up Service Discovery Backend!");
        this.applicationContext = (ApplicationContext) jsonObject.getValue("context");
        this.cache = (Cache<String, Record>) this.applicationContext.getCache("WhatsitServiceDiscoveryBackendCache");
    }

    @Override
    public void store(Record record, Handler<AsyncResult<Record>> handler) {
        Objects.requireNonNull(record.getRegistration(), "No registration id in the record!");

        if (this.cache.getIfPresent(record.getRegistration()) != null) {
            handler.handle(Future.failedFuture("The record has already been registered!"));
            return;
        }

        this.cache.put(record.getRegistration(), record);
        handler.handle(Future.succeededFuture(record));
    }

    @Override
    public void remove(Record record, Handler<AsyncResult<Record>> handler) {
        Objects.requireNonNull(record.getRegistration(), "No registration id in the record!");
        remove(record.getRegistration(), handler);
    }

    @Override
    public void remove(String s, Handler<AsyncResult<Record>> handler) {
        Objects.requireNonNull(s, "No registration id in the record!");

        this.cache.invalidate(s);
        handler.handle(Future.succeededFuture(new Record()));
    }

    @Override
    public void update(Record record, Handler<AsyncResult<Void>> handler) {
        Objects.requireNonNull(record.getRegistration(), "No registration id in the record!");
        this.cache.put(record.getRegistration(), record);
        handler.handle(Future.succeededFuture());
    }

    @Override
    public void getRecords(Handler<AsyncResult<List<Record>>> handler) {
        List<Record> list = new ArrayList<>();
        this.cache.asMap()
                .forEach((s, record) -> list.add(record));
        handler.handle(Future.succeededFuture(list));
    }

    @Override
    public void getRecord(String s, Handler<AsyncResult<Record>> handler) {
        Record record = this.cache.getIfPresent(s);
        handler.handle(Future.succeededFuture(record));
    }
}
