package org.jenkinsci.plugins.mesos.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.jenkinsci.plugins.mesos.MesosAgent;
import org.jenkinsci.plugins.mesos.MesosApi;
import org.jenkinsci.plugins.mesos.TestUtils.JenkinsParameterResolver;
import org.jenkinsci.plugins.mesos.TestUtils.JenkinsRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(JenkinsParameterResolver.class)
class MesosApiTest {

  @RegisterExtension static ZookeeperServerExtension zkServer = new ZookeeperServerExtension();

  static ActorSystem system = ActorSystem.create("mesos-scheduler-test");
  static ActorMaterializer materializer = ActorMaterializer.create(system);

  @RegisterExtension
  static MesosClusterExtension mesosCluster =
      MesosClusterExtension.builder()
          .withMesosMasterUrl(String.format("zk://%s/mesos", zkServer.getConnectionUrl()))
          .withLogPrefix(MesosApiTest.class.getCanonicalName())
          .build(system, materializer);

  @Test
  public void startAgent(JenkinsRule j)
      throws InterruptedException, ExecutionException, IOException, FormException,
          URISyntaxException {

    URL jenkinsUrl = j.getURL();

    String mesosUrl = mesosCluster.getMesosUrl();
    MesosApi api =
        new MesosApi(mesosUrl, jenkinsUrl, System.getProperty("user.name"), "MesosTest", "*");

    MesosAgent agent = api.enqueueAgent(null, 0.1, 32).toCompletableFuture().get();

    Awaitility.await().atMost(5, TimeUnit.MINUTES).until(agent::isRunning);
  }

  @Test
  public void stopAgent(JenkinsRule j) throws Exception {

    String mesosUrl = mesosCluster.getMesosUrl();
    URL jenkinsUrl = j.getURL();
    MesosApi api =
        new MesosApi(mesosUrl, jenkinsUrl, System.getProperty("user.name"), "MesosTest", "*");

    MesosAgent agent = api.enqueueAgent(null, 0.1, 32).toCompletableFuture().get();
    // Poll state until we get something.
    await().atMost(5, TimeUnit.MINUTES).until(agent::isRunning);
    assertThat(agent.isRunning(), equalTo(true));

    api.killAgent(agent.getPodId());
    await().atMost(5, TimeUnit.MINUTES).until(agent::isKilled);
    assertThat(agent.isKilled(), equalTo(true));
  }
}
