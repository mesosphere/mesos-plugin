package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.mesosphere.utils.PortAllocator;
import com.mesosphere.utils.mesos.MesosAgentConfig;
import com.mesosphere.utils.mesos.MesosCluster;
import com.mesosphere.utils.zookeeper.ZookeeperServer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import scala.Option;
import scala.collection.immutable.Vector$;
import scala.concurrent.duration.FiniteDuration;

public class ConnectionTest {
  @Test
  public void connectMesosApi() throws InterruptedException, ExecutionException {
    // TODO(karsten): Provide Zookeeper Server rules for teardown
    ZookeeperServer zkServer = new ZookeeperServer(true, PortAllocator.ephemeralPort());

    ActorSystem system = ActorSystem.create("mesos-scheduler-test");
    ActorMaterializer materializer = ActorMaterializer.create(system);

    String mesosMasterZkUrl = String.format("zk://%s/mesos", zkServer.connectUrl());
    int mesosNumMasters = 1;
    int mesosNumSlaves = 1;
    int mesosQuorumSize = 1;
    MesosAgentConfig agentConfig =
        new MesosAgentConfig("posix", "mesos", Option.empty(), Option.empty());
    FiniteDuration mesosLeaderTimeout = new FiniteDuration(30, TimeUnit.SECONDS);
    MesosCluster mesosCluster =
        new MesosCluster(
            "connection-test",
            mesosNumMasters,
            mesosNumSlaves,
            mesosMasterZkUrl,
            mesosQuorumSize,
            false,
            agentConfig,
            mesosLeaderTimeout,
            Vector$.MODULE$.empty(),
            Vector$.MODULE$.empty(),
            Option.empty(),
            system,
            materializer);

    // TODO(karsten): Provide Mesos cluster rules for teardown
    String mesosUrl = mesosCluster.start();

    try {
      System.out.println("Mesos " + mesosUrl);
      new MesosApi(mesosUrl, "example", "MesosTest");
    } finally {
      mesosCluster.close();
      zkServer.close();
    }
  }
}
