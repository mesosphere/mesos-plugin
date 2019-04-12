package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

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
      throws InterruptedException, ExecutionException, IOException, FormException,
          URISyntaxException {

    var jenkinsUrl = j.getURL();

    String mesosUrl = mesosCluster.getMesosUrl();
    MesosApi api = new MesosApi(mesosUrl, jenkinsUrl, "example", "MesosTest");

    MesosSlave agent = api.enqueueAgent().toCompletableFuture().get();

    Awaitility.await().atMost(1, TimeUnit.SECONDS).until(agent::isRunning);
  }
}
