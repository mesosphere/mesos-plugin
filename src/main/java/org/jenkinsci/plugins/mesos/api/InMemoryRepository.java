package org.jenkinsci.plugins.mesos.api;

import akka.Done;
import com.mesosphere.usi.core.models.PodId;
import com.mesosphere.usi.core.models.PodRecord;
import com.mesosphere.usi.repository.PodRecordRepository;
import java.util.AbstractMap;
import java.util.concurrent.ConcurrentHashMap;
import scala.collection.immutable.Map;
import scala.concurrent.Future;
import scala.concurrent.Future$;

public class InMemoryRepository implements PodRecordRepository {

  private final AbstractMap<PodId, PodRecord> data = new ConcurrentHashMap<>();

  @Override
  public Future<Done> delete(Object record) {
    return Future$.MODULE$.successful(Done.done());
  }

  @Override
  public Future<Done> store(Object record) {
    return Future$.MODULE$.successful(Done.done());
  }

  @Override
  public Future<Map<Object, Object>> readAll() {
    return null;
  }
}
