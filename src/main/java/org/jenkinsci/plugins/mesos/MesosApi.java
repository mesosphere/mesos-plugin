package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import com.mesosphere.mesos.client.MesosClient;
import com.mesosphere.mesos.client.MesosClient$;
import com.mesosphere.mesos.conf.MesosClientSettings;
import com.mesosphere.usi.core.japi.Scheduler;
import com.mesosphere.usi.core.models.*;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.mesos.v1.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.concurrent.ExecutionContext;

public class MesosApi {

  private static final Logger logger = LoggerFactory.getLogger(MesosApi.class);

  private final String slavesUser;
  private final String frameworkName;
  private final URL jenkinsUrl;
  private final Protos.FrameworkID frameworkId;
  private final MesosClientSettings clientSettings;
  private final MesosClient client;

  private final SourceQueueWithComplete<SpecUpdated> updates;
  private final ConcurrentHashMap<PodId, MesosSlave> stateMap;

  private final ActorSystem system;
  private final ActorMaterializer materializer;
  private final ExecutionContext context;

  /**
   * Establishes a connection to Mesos and provides a simple interface to start and stop {@link
   * MesosSlave} instances.
   *
   * @param masterUrl The Mesos master address to connect to.
   * @param jenkinsUrl The Jenkins address to fetch the agent jar from.
   * @param user The username used for executing Mesos tasks.
   * @param frameworkName The name of the framework the Mesos client should register as.
   * @throws InterruptedException
   * @throws ExecutionException
   */
  public MesosApi(String masterUrl, URL jenkinsUrl, String user, String frameworkName)
      throws InterruptedException, ExecutionException {
    this.frameworkName = frameworkName;
    this.frameworkId =
        Protos.FrameworkID.newBuilder().setValue(UUID.randomUUID().toString()).build();
    this.slavesUser = user;
    this.jenkinsUrl = jenkinsUrl;

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
    updates = runUsi(SpecsSnapshot.empty(), client, materializer).get();
  }

  /**
   * Constructs a queue of {@link SpecUpdated} and passes the specs snapshot as the first item. All
   * updates are processed by {@link MesosApi#updateState(StateEvent)}.
   *
   * @param specsSnapshot The initial set of pod specs.
   * @param client The Mesos client that is used.
   * @param materializer The {@link ActorMaterializer} used for the source queue.
   * @return A running source queue.
   */
  private CompletableFuture<SourceQueueWithComplete<SpecUpdated>> runUsi(
      SpecsSnapshot specsSnapshot, MesosClient client, ActorMaterializer materializer) {
    return Scheduler.asFlow(specsSnapshot, client, materializer)
        .thenApply(
            builder -> {
              // We create a SourceQueue and assume that the very first item is a spec snapshot.
              var queue =
                  Source.<SpecUpdated>queue(256, OverflowStrategy.fail())
                      .via(builder.getFlow())
                      .toMat(Sink.foreach(this::updateState), Keep.left())
                      .run(materializer);

              return queue;
            });
  }

  /**
   * Enqueue spec for a Jenkins event, passing a non-null existing podId will trigger a kill for
   * that pod
   *
   * @return a {@link MesosSlave} once it's queued for running.
   */
  public CompletionStage<Void> killAgent(String id) throws Exception {
    PodSpec spec = stateMap.get(new PodId(id)).getPodSpec(0.1, 32, Goal.Terminal$.MODULE$);
    SpecUpdated update = new PodSpecUpdated(spec.id(), Option.apply(spec));
    return updates.offer(update).thenRun(() -> {});
  }

  /**
   * Enqueue spec for a Jenkins event, passing a non-null existing podId will trigger a kill for
   * that pod
   *
   * @return a {@link MesosSlave} once it's queued for running.
   */
  public CompletionStage<MesosSlave> enqueueAgent(MesosCloud cloud, double cpu, int mem)
      throws IOException, FormException, URISyntaxException {

    var name = String.format("jenkins-test-%s", UUID.randomUUID().toString());
    MesosSlave mesosSlave =
        new MesosSlave(cloud, name, "Mesos Jenkins Slave", jenkinsUrl, "label", List.of());
    PodSpec spec = mesosSlave.getPodSpec(cpu, mem, Goal.Running$.MODULE$);
    SpecUpdated update = new PodSpecUpdated(spec.id(), Option.apply(spec));

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

  public ActorMaterializer getMaterializer() {
    return materializer;
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
    // TODO: kill pod if unknown.
  }
}
