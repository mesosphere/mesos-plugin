package org.jenkinsci.plugins.mesos.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.SourceQueueWithComplete;
import com.mesosphere.usi.core.models.SchedulerCommand;
import com.mesosphere.usi.core.models.StateEventOrSnapshot;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.Descriptor.FormException;
import hudson.model.Node.Mode;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate;
import org.jenkinsci.plugins.mesos.MesosApi;
import org.jenkinsci.plugins.mesos.MesosJenkinsAgent;
import org.jenkinsci.plugins.mesos.TestUtils.JenkinsParameterResolver;
import org.jenkinsci.plugins.mesos.TestUtils.JenkinsRule;
import org.jenkinsci.plugins.mesos.fixture.AgentSpecMother;
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

    URL mesosUrl = mesosCluster.getMesosUrl();
    MesosApi api =
        new MesosApi(mesosUrl, jenkinsUrl, System.getProperty("user.name"), "MesosTest", "*");

    final String name = "jenkins-start-agent";
    final MesosAgentSpecTemplate spec = AgentSpecMother.simple;

    MesosJenkinsAgent agent = api.enqueueAgent(name, spec).toCompletableFuture().get();

    Awaitility.await().atMost(5, TimeUnit.MINUTES).until(agent::isRunning);
  }

  @Test
  public void stopAgent(JenkinsRule j) throws Exception {

    URL mesosUrl = mesosCluster.getMesosUrl();
    URL jenkinsUrl = j.getURL();
    MesosApi api =
        new MesosApi(mesosUrl, jenkinsUrl, System.getProperty("user.name"), "MesosTest", "*");
    final String name = "jenkins-stop-agent";
    final MesosAgentSpecTemplate spec = AgentSpecMother.simple;

    MesosJenkinsAgent agent = api.enqueueAgent(name, spec).toCompletableFuture().get();
    // Poll state until we get something.
    await().atMost(5, TimeUnit.MINUTES).until(agent::isRunning);
    assertThat(agent.isRunning(), equalTo(true));

    api.killAgent(agent.getPodId());
    await().atMost(5, TimeUnit.MINUTES).until(agent::isKilled);
    assertThat(agent.isKilled(), equalTo(true));
  }

  @Test
  public void testLaunchOverflow() throws Exception {
    // Given a scheduler flow that never processes commands.
    final CompletableFuture<StateEventOrSnapshot> never = new CompletableFuture<>();
    final Flow<SchedulerCommand, StateEventOrSnapshot, NotUsed> schedulerFlow =
        Flow.of(SchedulerCommand.class).mapAsync(1, command -> never);

    MesosApi api =
        new MesosApi(
            new URL("http://jenkins.com"),
            System.getProperty("user.name"),
            "MesosTest",
            "uniqueId",
            "*",
            schedulerFlow,
            system,
            materializer);

    // When we enqueue an agent
    api.enqueueAgent("agent1", AgentSpecMother.simple);
    api.enqueueAgent("agent2", AgentSpecMother.simple).toCompletableFuture().get(1, TimeUnit.SECONDS);


    // Then backpressure hits us.
  }
}
