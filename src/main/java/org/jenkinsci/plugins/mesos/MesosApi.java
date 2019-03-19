package org.jenkinsci.plugins.mesos;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.tuple.Tuple3;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.mesosphere.mesos.client.MesosClient;
import com.mesosphere.mesos.client.MesosClient$;
import com.mesosphere.mesos.conf.MesosClientSettings;
import com.mesosphere.usi.core.models.*;
import com.mesosphere.usi.core.models.resources.ScalarRequirement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import com.mesosphere.usi.core.javaapi.SchedulerAdapter;
import com.mesosphere.usi.core.Scheduler;
import scala.Option;
import scala.collection.JavaConverters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang.NotImplementedException;
import org.apache.mesos.v1.Protos;
import scala.collection.Seq;
import scala.concurrent.ExecutionContext;

public class MesosApi {

  private final String slavesUser;
  private final String frameworkName;
  private final Protos.FrameworkID frameworkId;
  private final MesosClientSettings clientSettings;
  private final MesosClient client;
  private final Tuple3<CompletableFuture<StateSnapshot>, Source<StateEvent, NotUsed>, Sink<SpecUpdated, NotUsed>> adapter;
  private final ConcurrentHashMap<PodId, MesosComputer> stateMap;

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
    var scheduler = Scheduler.fromClient(client);
    adapter = new SchedulerAdapter(Scheduler.fromClient(client), materializer, context)
                .asSourceAndSink(SpecsSnapshot.empty());
    adapter.t2().runWith(Sink.foreach(this::updateState), materializer);
  }

  /**
   * Start a Jekins slave/agent on Mesos.
   *
   * @return A future reference to the {@link MesosSlave}.
   */
  public void startAgent() {
    PodSpec spec = buildMesosAgentTask(0.1, 32);
    SpecUpdated update = new PodSpecUpdated(spec.id(), Option.apply(spec));
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
            .setFailoverTimeout(0d)
            .build();

    return MesosClient$.MODULE$
        .apply(clientSettings, frameworkInfo, system, materializer)
        .runWith(Sink.head(), materializer)
        .toCompletableFuture();
  }

  private PodSpec buildMesosAgentTask(double cpu, double mem) {
    RunSpec spec = new RunSpec(
        convertListToSeq(Arrays.asList(ScalarRequirement.cpus(cpu), ScalarRequirement.memory(mem))),
        "echo Hello!",
        convertListToSeq(Collections.emptyList())
    );
    String id = UUID.randomUUID().toString();
    PodSpec podSpec = new PodSpec(
        new PodId(String.format("jenkins-test-%s", id)),
        new Goal.Running$(),
        spec
    );
    return podSpec;
  }

  public void updateState(StateEvent event) {
    throw new NotImplementedException();
    /*
    if (event instanceof PodStateEvent) {
      stateMap.put(((PodStateEvent) event).id(), )
    } */
  }

  private <T> Seq<T> convertListToSeq(List<T> inputList) {
    return JavaConverters.asScalaIteratorConverter(inputList.iterator()).asScala().toSeq();
  }
}
