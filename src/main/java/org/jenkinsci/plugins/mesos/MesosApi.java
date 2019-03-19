package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import com.mesosphere.mesos.client.MesosClient;
import com.mesosphere.mesos.client.MesosClient$;
import com.mesosphere.mesos.conf.MesosClientSettings;
import com.mesosphere.usi.core.models.*;
import com.mesosphere.usi.core.models.resources.ResourceRequirement;
import com.mesosphere.usi.core.models.resources.ScalarRequirement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import com.mesosphere.usi.core.javaapi.SchedulerAdapter;
import com.mesosphere.usi.core.Scheduler;
import scala.collection.JavaConverters;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
  private final SchedulerAdapter adapter;

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

    adapter = new SchedulerAdapter(Scheduler.fromClient(client), materializer, context);
  }

  /**
   * Start a Jekins slave/agent on Mesos.
   *
   * @return A future reference to the {@link MesosSlave}.
   */
  public CompletableFuture<MesosSlave> startAgent() {
    PodSpec spec = buildMesosAgentTask(0.1, 32);
    SpecsSnapshot snapshot = new SpecsSnapshot(convertListToSeq(Arrays.asList(spec)), null);
    adapter.asAkkaQueues(snapshot, OverflowStrategy.backpressure()).t1();

    throw new NotImplementedException();
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
    PodSpec podSpec = new PodSpec(
        new PodId("jenkins-test"),
        new Goal.Running$(),
        spec
    );
    return podSpec;
  }

  private <T> Seq<T> convertListToSeq(List<T> inputList) {
    return JavaConverters.asScalaIteratorConverter(inputList.iterator()).asScala().toSeq();
  }
}
