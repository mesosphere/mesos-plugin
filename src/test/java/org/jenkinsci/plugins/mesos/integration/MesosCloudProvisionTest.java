package org.jenkinsci.plugins.mesos.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node.Mode;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate;
import org.jenkinsci.plugins.mesos.MesosCloud;
import org.jenkinsci.plugins.mesos.MesosJenkinsAgent;
import org.jenkinsci.plugins.mesos.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosCloudProvisionTest {

  @RegisterExtension static ZookeeperServerExtension zkServer = new ZookeeperServerExtension();

  static ActorSystem system = ActorSystem.create("mesos-scheduler-test");
  static ActorMaterializer materializer = ActorMaterializer.create(system);

  @RegisterExtension
  static MesosClusterExtension mesosCluster =
      MesosClusterExtension.builder()
          .withMesosMasterUrl(String.format("zk://%s/mesos", zkServer.getConnectionUrl()))
          .withLogPrefix(MesosCloudProvisionTest.class.getCanonicalName())
          .build(system, materializer);

  @Test
  public void testJenkinsProvision(TestUtils.JenkinsRule j) throws Exception {
    LabelAtom label = new LabelAtom("label");
    final MesosAgentSpecTemplate spec =
        new MesosAgentSpecTemplate(label.toString(), Mode.EXCLUSIVE);
    List<MesosAgentSpecTemplate> specTemplates = Collections.singletonList(spec);

    MesosCloud cloud =
        new MesosCloud(
            mesosCluster.getMesosUrl(),
            "MesosTest",
            "*",
            System.getProperty("user.name"),
            j.getURL().toString(),
            specTemplates);

    int workload = 3;
    Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(label, workload);

    assertThat(plannedNodes, hasSize(workload));
    for (NodeProvisioner.PlannedNode node : plannedNodes) {
      // resolve all plannedNodes
      MesosJenkinsAgent agent = (MesosJenkinsAgent) node.future.get();

      // ensure all plannedNodes are now running
      assertThat(agent.isRunning(), is(true));
    }

    // check that jenkins knows about all the plannedNodes
    assertThat(Jenkins.getInstanceOrNull().getNodes(), hasSize(workload));
  }

  @Test
  public void testStartAgent(TestUtils.JenkinsRule j) throws Exception {
    final String name = "jenkins-agent";
    final MesosAgentSpecTemplate spec = new MesosAgentSpecTemplate(name, Mode.EXCLUSIVE);
    List<MesosAgentSpecTemplate> specTemplates = Collections.singletonList(spec);
    MesosCloud cloud =
        new MesosCloud(
            mesosCluster.getMesosUrl(),
            "MesosTest",
            "*",
            System.getProperty("user.name"),
            j.getURL().toString(),
            specTemplates);

    MesosJenkinsAgent agent = (MesosJenkinsAgent) cloud.startAgent(name, spec).get();

    await().atMost(5, TimeUnit.MINUTES).until(agent::isRunning);

    assertThat(agent.isRunning(), is(true));
    assertThat(agent.isOnline(), is(true));

    // assert jenkins has the 1 added nodes
    assertThat(Jenkins.getInstanceOrNull().getNodes(), hasSize(1));
  }

  @Test
  public void runSimpleBuild(TestUtils.JenkinsRule j) throws Exception {

    WebClient c = j.createWebClient();
    c.setJavaScriptEnabled(true);

    HtmlForm config = c.goTo("configure").getFormByName("config");

    // Add a new Mesos Cloud
    final HtmlPage p = ((HtmlButton) config.getFirstByXPath("//button[@suffix=\"cloud\"]")).click();
    final HtmlPage pp = ((HtmlAnchor) p.getFirstByXPath("//a[text()=\"Mesos Cloud\"]")).click();

    // Wait until Mesos Cloud form shows up.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .until(() -> pp.getElementByName("_.mesosMasterUrl") != null);

    // Fill out Mesos Cloud form and submit it.
    pp.getElementByName("_.mesosMasterUrl").setAttribute("value", mesosCluster.getMesosUrl());
    pp.getElementByName("_.jenkinsUrl").setAttribute("value", j.getURL().toString());
    pp.getElementByName("_.agentUser").setAttribute("value", System.getProperty("user.name"));

    final HtmlPage ppp =
        ((HtmlButton)
                pp.getFirstByXPath("//div[@class=\"repeated-container\"]//button[text()=\"Add\"]"))
            .click();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .ignoreExceptions()
        .until(() -> ppp.getElementByName("mesosAgentSpecTemplates") != null);

    ((HtmlButton) ppp.getFirstByXPath("//span[@name=\"Submit\"]//button")).click();

    // Verify that everything was saved.
    final String savedMesosUrl =
        j.createWebClient()
            .goTo("configure")
            .getFormByName("config")
            .getInputByName("_.mesosMasterUrl")
            .getValueAttribute();
    assertThat(savedMesosUrl, is(equalTo(mesosCluster.getMesosUrl())));

    // Given: a project with a simple build command.
    FreeStyleProject project = j.createFreeStyleProject("mesos-test");
    final Builder step = new Shell("echo Hello");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom("mesos"));

    FreeStyleBuild build = j.buildAndAssertSuccess(project);

    j.assertLogContains("echo Hello", build);
  }
}
