package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import com.mesosphere.mesos.client.MesosClient;
import com.mesosphere.mesos.client.MesosClient$;
import com.mesosphere.mesos.conf.MesosClientSettings;
import com.mesosphere.usi.core.javaapi.Scheduler;
import com.mesosphere.usi.core.models.*;
import com.mesosphere.usi.core.models.Goal.Running$;
import com.mesosphere.usi.core.models.resources.ScalarRequirement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.mesos.v1.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.concurrent.ExecutionContext;

public class MesosApi {

  private static final Logger logger = LoggerFactory.getLogger(MesosApi.class);

  private final String slavesUser;
  private final String frameworkName;
  private final Protos.FrameworkID frameworkId;
  private final MesosClientSettings clientSettings;
  private final MesosClient client;

  private final SourceQueueWithComplete<SpecEvent> updates;
  private final ConcurrentHashMap<PodId, MesosSlave> stateMap;

  private final ActorSystem system;
  private final ActorMaterializer materializer;
  private final ExecutionContext context;

  /**
   * Establishes a connection to Mesos and provides a simple interface to start and stop {@link
   * MesosSlave} instances.
   *
   * @param masterUrl The Mesos master address to connect to.
   * @param user The username used for executing Mesos tasks.
   * @param frameworkName The name of the framework the Mesos client should register as.
   * @throws InterruptedException
   * @throws ExecutionException
   */
  public MesosApi(String masterUrl, String user, String frameworkName)
      throws InterruptedException, ExecutionException {
    this.frameworkName = frameworkName;
    this.frameworkId =
        Protos.FrameworkID.newBuilder().setValue(UUID.randomUUID().toString()).build();
    this.slavesUser = user;

    Config conf =
        ConfigFactory.load()
            .getConfig("mesos-client")
            .withValue("master-url", ConfigValueFactory.fromAnyRef(masterUrl));
    this.clientSettings = MesosClientSettings.fromConfig(conf);
    system = ActorSystem.create("mesos-scheduler");
    context = system.dispatcher();
    materializer = ActorMaterializer.create(system);

    client = connectClient().get();

    stateMap = new ConcurrentHashMap<>();

    logger.info("Starting USI scheduler flow.");
    updates = runUsi(SpecsSnapshot.empty(), client, materializer);
  }

  private SourceQueueWithComplete<SpecEvent> runUsi(
      SpecsSnapshot specsSnapshot, MesosClient client, ActorMaterializer materializer) {
    var schedulerFlow = Scheduler.fromClient(client);

    var queue =
        Source.<SpecEvent>queue(256, OverflowStrategy.fail())
            .prefixAndTail(1)
            .map(
                pair -> {
                  var snapshot = (SpecsSnapshot) pair.first();
                  Source<SpecUpdated, Object> updates =
                      pair.second()
                          .map(event -> (SpecUpdated) event)
                          .mapMaterializedValue(notUsed -> (Object) notUsed);
                  return new Pair<SpecsSnapshot, Source<SpecUpdated, Object>>(snapshot, updates);
                })
            .via(schedulerFlow)
            .flatMapConcat(pair -> pair.second())
            .toMat(Sink.foreach(this::updateState), Keep.left())
            .run(materializer);

    queue.offer(specsSnapshot);
    return queue;
  }

  /**
   * Enqueue spec for a Jenkins agent that will eventually come online.
   *
   * @return a {@link MesosSlave} once it's queued for running.
   */
  public CompletionStage<MesosSlave> enqueueAgent() throws IOException, FormException {
    PodSpec spec = buildMesosAgentTask(0.1, 32);
    SpecUpdated update = new PodSpecUpdated(spec.id(), Option.apply(spec));

    MesosSlave mesosSlave =
        new MesosSlave(spec.id().value(), "Mesos Jenkins Slave", "label", List.of());

    stateMap.put(spec.id(), mesosSlave);

    // async add agent to queue
    return updates.offer(update).thenApply(result -> mesosSlave); // TODO: handle QueueOfferResult.
  }

  /** Establish a connection to Mesos via the v1 client. */
  private CompletableFuture<MesosClient> connectClient() {
    Protos.FrameworkInfo frameworkInfo =
        Protos.FrameworkInfo.newBuilder()
            .setUser(slavesUser)
            .setName(frameworkName)
            .setId(frameworkId)
            .addRoles("test")
            .addCapabilities(
                Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE))
            .setFailoverTimeout(0d) // Use config from current Mesos plugin.
            .build();

    return MesosClient$.MODULE$
        .apply(clientSettings, frameworkInfo, system, materializer)
        .runWith(Sink.head(), materializer)
        .toCompletableFuture();
  }

  private PodSpec buildMesosAgentTask(double cpu, double mem) {
    RunSpec spec =
        new RunSpec(
            convertListToSeq(
                Arrays.asList(ScalarRequirement.cpus(cpu), ScalarRequirement.memory(mem))),
            "echo Hello! && sleep 1000000",
            convertListToSeq(Collections.emptyList()));
    String id = UUID.randomUUID().toString();
    PodSpec podSpec =
        new PodSpec(new PodId(String.format("jenkins-test-%s", id)), Running$.MODULE$, spec);
    return podSpec;
  }

  /**
   * Callback for USI to process state events.
   *
   * <p>This method will filter out {@link PodStatusUpdated} and pass them on to their {@link
   * MesosSlave}. It should be threadsafe.
   *
   * @param event The {@link PodStatusUpdated} for a USI pod.
   */
  public void updateState(StateEvent event) {
    if (event instanceof PodStatusUpdated) {
      var podStateEvent = (PodStatusUpdated) event;
      logger.info("Got status update for pod {}", podStateEvent.id().value());
      stateMap.computeIfPresent(
          podStateEvent.id(),
          (id, slave) -> {
            slave.update(podStateEvent);
            return slave;
          });
    }
  }

  private <T> Seq<T> convertListToSeq(List<T> inputList) {
    return JavaConverters.asScalaIteratorConverter(inputList.iterator()).asScala().toSeq();
  }
}
