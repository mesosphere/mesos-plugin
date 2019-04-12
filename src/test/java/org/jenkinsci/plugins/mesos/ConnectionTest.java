package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
class ConnectionTest {

  @RegisterExtension static ZookeeperServerExtension zkServer = new ZookeeperServerExtension();

  static ActorSystem system = ActorSystem.create("mesos-scheduler-test");
  static ActorMaterializer materializer = ActorMaterializer.create(system);

  @RegisterExtension
  static MesosClusterExtension mesosCluster =
      MesosClusterExtension.builder()
          .withMesosMasterUrl(String.format("zk://%s/mesos", zkServer.getConnectionUrl()))
          .withLogPrefix(ConnectionTest.class.getCanonicalName())
          .build(system, materializer);

  @Test
  public void startAgent(TestUtils.JenkinsRule j)
      throws InterruptedException, ExecutionException, IOException, FormException {

    String mesosUrl = mesosCluster.getMesosUrl();
    MesosApi api = new MesosApi(mesosUrl, "example", "MesosTest");

    MesosCloud cloud = Mockito.mock(MesosCloud.class);

    MesosSlave agent = api.enqueueAgent(cloud, 0.1, 32).toCompletableFuture().get();

    // Poll state until we get something.
    while (!agent.isRunning()) {
      Thread.sleep(1000);
      System.out.println("not running yet");
    }
  }

  /* @Test
  public void stopAgent(TestUtils.JenkinsRule j)
          throws InterruptedException, ExecutionException, IOException, FormException {

    String mesosUrl = mesosCluster.getMesosUrl();
    MesosApi api = new MesosApi(mesosUrl, "example", "MesosTest");

    MesosSlave agent = api.enqueueAgent(0.1, 32, Optional.empty()).toCompletableFuture().get();

    // Poll state until we get something.
    while (!agent.isRunning()) {
      Thread.sleep(1000);
      System.out.println("not running yet");
    }
  } */
}
