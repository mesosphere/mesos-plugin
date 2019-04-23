package org.jenkinsci.plugins.mesos.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
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
import io.webfolder.ui4j.api.browser.BrowserEngine;
import io.webfolder.ui4j.api.browser.BrowserFactory;
import io.webfolder.ui4j.api.browser.Page;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate;
import org.jenkinsci.plugins.mesos.MesosCloud;
import org.jenkinsci.plugins.mesos.MesosJenkinsAgent;
import org.jenkinsci.plugins.mesos.TestUtils;
import org.jenkinsci.plugins.mesos.TestUtils.JenkinsRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.runner.Description;
import org.jvnet.hudson.test.HudsonHomeLoader.Local;
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
    System.out.println(j.createWebClient().createCrumbedUrl("configure"));
    BrowserEngine browser = BrowserFactory.getWebKit();
    String url = j.getURL().toURI().resolve("/jenkins").toString();
    System.out.println("+++ " + url);
//    Page page = browser.navigate();
    Page page = browser.navigate(url);
    System.out.println(page.getDocument().getBody().getText());
//    WebClient c = j.jcreateWebClient();
//    c.setJavaScriptEnabled(true);
//
//    HtmlButton cloud = c.goTo("configure").getFirstByXPath("//*[@suffix=\"cloud\"]");
//    System.out.println(cloud);
//    HtmlPage mesosCloud = ((HtmlPage) cloud.click()).getAnchorByText("Mesos Cloud").click();
//
//    System.out.println(mesosCloud.getElementByName("_.mesosMasterUrl"));

//    HtmlForm config = c.goTo("configure").getFormByName("config");

//    HtmlElement numExecutorsInput = config.appendChildIfNoneExists("input");
//    numExecutorsInput.setAttribute("name", "_.numExecutors");
//    numExecutorsInput.setAttribute("value", "2");
//
//    HtmlElement mesosMasterUrlInput = config.appendChildIfNoneExists("input");
//    mesosMasterUrlInput.setAttribute("name", "_.mesosMasterUrl");
//    mesosMasterUrlInput.setAttribute("value", mesosCluster.getMesosUrl());
//
//    HtmlElement agentUserInput = config.appendChildIfNoneExists("input");
//    agentUserInput.setAttribute("name", "_.agentUser");
//    agentUserInput.setAttribute("value", System.getProperty("user.name"));
//
//    HtmlElement jenkinsUrlInput = config.appendChildIfNoneExists("input");
//    jenkinsUrlInput.setAttribute("name", "_.jenkinsUrl");
//    jenkinsUrlInput.setAttribute("value", j.getURL().toString());

//    j.submit(config);
//    System.out.println(j.createWebClient().goTo("configure").getFormByName("config").getInputByName("_.mesosMasterUrl"));


    // Given: a project with a simple build command.
//    FreeStyleProject project = j.createFreeStyleProject("mesos-test");
//    final Builder step = new Shell("echo Hello");
//    project.getBuildersList().add(step);
//    project.setAssignedLabel(new LabelAtom("mesos"));

//    FreeStyleBuild build = j.buildAndAssertSuccess(project);

//    j.assertLogContains("echo Hello", build);
  }
}
