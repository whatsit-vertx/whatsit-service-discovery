package io.github.pangzixiang.whatsit.vertx.service.discovery.backend;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.auto.service.AutoService;
import io.github.pangzixiang.whatsit.vertx.core.context.ApplicationContext;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.Status;
import io.vertx.servicediscovery.spi.ServiceDiscoveryBackend;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        Record existRecord = getRecordByServiceName(record.getName());
        if (existRecord == null) {
            log.info("Register new record (name={})(id={})",
                    record.getName(), record.getRegistration());
            this.cache.put(record.getRegistration(), record);
            handler.handle(Future.succeededFuture(record));
        } else {
            log.info("Refresh record (name={})(id={})(newId={})",
                    record.getName(), existRecord.getRegistration(), record.getRegistration());
            this.cache.invalidate(existRecord.getRegistration());
            existRecord.setMetadata(new JsonObject().put("lastUpdated", LocalDateTime.now()));
            existRecord.setRegistration(record.getRegistration());
            existRecord.setStatus(Status.UP);
            this.cache.put(record.getRegistration(), existRecord);

            handler.handle(Future.succeededFuture(existRecord));
        }
    }

    @Override
    public void remove(Record record, Handler<AsyncResult<Record>> handler) {
        Objects.requireNonNull(record.getRegistration(), "No registration id in the record!");
        remove(record.getRegistration(), handler);
    }

    @Override
    public void remove(String s, Handler<AsyncResult<Record>> handler) {
        Objects.requireNonNull(s, "No registration id in the record!");

        log.info("Remove record (id={})", s);
        Record record = this.cache.getIfPresent(s);
        if (record != null) {
            this.cache.invalidate(s);
            handler.handle(Future.succeededFuture(record.setStatus(Status.DOWN)));
        } else {
            handler.handle(Future.succeededFuture(new Record()));
        }
    }

    @Override
    public void update(Record record, Handler<AsyncResult<Void>> handler) {
        Objects.requireNonNull(record.getRegistration(), "No registration id in the record!");
        log.info("Update Record (name={})(id={})", record.getName(), record.getRegistration());
        this.cache.put(record.getRegistration(), record);
        handler.handle(Future.succeededFuture());
    }

    @Override
    public void getRecords(Handler<AsyncResult<List<Record>>> handler) {
        List<Record> list = new ArrayList<>();
        this.cache.asMap()
                .forEach((s, record) -> list.add(record));
        log.info("Get all records (size={})", list.size());
        handler.handle(Future.succeededFuture(list));
    }

    @Override
    public void getRecord(String s, Handler<AsyncResult<Record>> handler) {
        log.info("Get record (id={})", s);
        Record record = this.cache.getIfPresent(s);
        handler.handle(Future.succeededFuture(record));
    }

    private Record getRecordByServiceName(String name) {
        for (Map.Entry<String, Record> entry : this.cache.asMap().entrySet()) {
            Record record = entry.getValue();
            if (record.getName().equals(name)) {
                return record;
            }
        }
        return null;
    }
}
