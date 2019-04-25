package org.jenkinsci.plugins.mesos.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
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
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tools.ant.taskdefs.Javadoc.Html;
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

    OkHttpClient client = new OkHttpClient();

    final String jenkinsUrl = j.getURL().toURI().resolve("jenkins").toString();
    FormBody formBody = new FormBody.Builder()
        .addEncoded("_.numExecutors", "2")
        .addEncoded("_.labelString", "")
        .addEncoded("master.mode", "NORMAL")
        .addEncoded("_.quietPeriod", "5")
        .addEncoded("_.scmCheckoutRetryCount", "0")
        .addEncoded("stapler-class", "jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy")
        .addEncoded("$class", "jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy")
        .addEncoded("_.namePattern", "*")
        .addEncoded("_.description", "")
        .addEncoded("namingStrategy", "1")
        .addEncoded("stapler-class", "jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy")
        .addEncoded("$class", "jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy")
        .addEncoded("_.usageStatisticsCollected", "on")
        .addEncoded("administrativeMonitor", "on")
        .addEncoded("_.url",jenkinsUrl )
        .addEncoded("_.adminAddress", "")
        .addEncoded("_.shell", "")
        .addEncoded("_.mesosMasterUrl", mesosCluster.getMesosUrl())
        .addEncoded("_.role", "*")
        .addEncoded("_.agentUser", System.getProperty("user.name"))
        .addEncoded("_.jenkinsUrl", jenkinsUrl)
        .addEncoded("_.label", "mesos")
        .addEncoded("mode", "EXCLUSIVE")
        .addEncoded("stapler-class", "org.jenkinsci.plugins.mesos.MesosCloud")
        .addEncoded("$class", "org.jenkinsci.plugins.mesos.MesosCloud")
        .addEncoded("core:apply", "")
        .build();

    final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");
    final String data ="system_message=&_.numExecutors=2&_.labelString=&master.mode=NORMAL&_.quietPeriod=5&_.scmCheckoutRetryCount=0&stapler-class=jenkins.model.ProjectNamingStrategy%24PatternProjectNamingStrategy&%24class=jenkins.model.ProjectNamingStrategy%24PatternProjectNamingStrategy&_.namePattern=.*&_.description=&namingStrategy=1&stapler-class=jenkins.model.ProjectNamingStrategy%24DefaultProjectNamingStrategy&%24class=jenkins.model.ProjectNamingStrategy%24DefaultProjectNamingStrategy&_.usageStatisticsCollected=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&administrativeMonitor=on&_.url="+URLEncoder.encode(jenkinsUrl, "UTF-8")+"&_.adminAddress=Adresse+nicht+konfiguriert+%3Cnobody%40nowhere%3E&_.shell=&_.mesosMasterUrl="+ URLEncoder
        .encode(mesosCluster.getMesosUrl(), "UTF-8")+"&_.frameworkName=Jenkins+Scheduler&_.role=*&_.agentUser="+URLEncoder.encode(System.getProperty("user.name"), "UTF-8")+"&_.jenkinsUrl="+URLEncoder.encode(jenkinsUrl, "UTF-8")+"&_.label=mesos&mode=EXCLUSIVE&stapler-class=org.jenkinsci.plugins.mesos.MesosCloud&%24class=org.jenkinsci.plugins.mesos.MesosCloud&core%3Aapply=&json=%7B%22system_message%22%3A+%22%22%2C+%22jenkins-model-MasterBuildConfiguration%22%3A+%7B%22numExecutors%22%3A+%222%22%2C+%22labelString%22%3A+%22%22%2C+%22mode%22%3A+%22NORMAL%22%7D%2C+%22jenkins-model-GlobalQuietPeriodConfiguration%22%3A+%7B%22quietPeriod%22%3A+%225%22%7D%2C+%22jenkins-model-GlobalSCMRetryCountConfiguration%22%3A+%7B%22scmCheckoutRetryCount%22%3A+%220%22%7D%2C+%22jenkins-model-GlobalProjectNamingStrategyConfiguration%22%3A+%7B%7D%2C+%22jenkins-model-GlobalNodePropertiesConfiguration%22%3A+%7B%22globalNodeProperties%22%3A+%7B%7D%7D%2C+%22hudson-model-UsageStatistics%22%3A+%7B%22usageStatisticsCollected%22%3A+%7B%7D%7D%2C+%22jenkins-management-AdministrativeMonitorsConfiguration%22%3A+%7B%22administrativeMonitor%22%3A+%5B%22hudson.PluginManager%24PluginCycleDependenciesMonitor%22%2C+%22hudson.PluginManager%24PluginUpdateMonitor%22%2C+%22hudson.PluginWrapper%24PluginWrapperAdministrativeMonitor%22%2C+%22hudsonHomeIsFull%22%2C+%22hudson.diagnosis.NullIdDescriptorMonitor%22%2C+%22OldData%22%2C+%22hudson.diagnosis.ReverseProxySetupMonitor%22%2C+%22hudson.diagnosis.TooManyJobsButNoView%22%2C+%22hudson.model.UpdateCenter%24CoreUpdateMonitor%22%2C+%22hudson.node_monitors.MonitorMarkedNodeOffline%22%2C+%22hudson.triggers.SCMTrigger%24AdministrativeMonitorImpl%22%2C+%22jenkins.CLI%22%2C+%22jenkins.diagnosis.HsErrPidList%22%2C+%22jenkins.diagnostics.CompletedInitializationMonitor%22%2C+%22jenkins.diagnostics.RootUrlNotSetMonitor%22%2C+%22jenkins.diagnostics.SecurityIsOffMonitor%22%2C+%22jenkins.diagnostics.URICheckEncodingMonitor%22%2C+%22jenkins.model.DownloadSettings%24Warning%22%2C+%22jenkins.model.Jenkins%24EnforceSlaveAgentPortAdministrativeMonitor%22%2C+%22jenkins.security.RekeySecretAdminMonitor%22%2C+%22jenkins.security.UpdateSiteWarningsMonitor%22%2C+%22jenkins.security.apitoken.ApiTokenPropertyDisabledDefaultAdministrativeMonitor%22%2C+%22jenkins.security.apitoken.ApiTokenPropertyEnabledNewLegacyAdministrativeMonitor%22%2C+%22legacyApiToken%22%2C+%22jenkins.security.csrf.CSRFAdministrativeMonitor%22%2C+%22slaveToMasterAccessControl%22%2C+%22jenkins.security.s2m.MasterKillSwitchWarning%22%2C+%22jenkins.slaves.DeprecatedAgentProtocolMonitor%22%5D%7D%2C+%22jenkins-model-JenkinsLocationConfiguration%22%3A+%7B%22url%22%3A+%22http%3A%2F%2Flocalhost%3A8080%2F%22%2C+%22adminAddress%22%3A+%22Adresse+nicht+konfiguriert+%3Cnobody%40nowhere%3E%22%7D%2C+%22hudson-tasks-Shell%22%3A+%7B%22shell%22%3A+%22%22%7D%2C+%22jenkins-model-GlobalCloudConfiguration%22%3A+%7B%22cloud%22%3A+%7B%22mesosMasterUrl%22%3A+%22"+URLEncoder.encode(mesosCluster.getMesosUrl(), "UTF-8")+"%22%2C+%22frameworkName%22%3A+%22Jenkins+Scheduler%22%2C+%22role%22%3A+%22*%22%2C+%22agentUser%22%3A+%22"+System.getProperty("user.name")+"%22%2C+%22jenkinsUrl%22%3A+%22"+URLEncoder.encode(jenkinsUrl, "UTF-8")+"%22%2C+%22mesosAgentSpecTemplates%22%3A+%7B%22label%22%3A+%22mesos%22%2C+%22mode%22%3A+%22EXCLUSIVE%22%7D%2C+%22stapler-class%22%3A+%22org.jenkinsci.plugins.mesos.MesosCloud%22%2C+%22%24class%22%3A+%22org.jenkinsci.plugins.mesos.MesosCloud%22%7D%7D%2C+%22core%3Aapply%22%3A+%22%22%7D";
    RequestBody rawBody = RequestBody.create(FORM, data);

    final String requestUrl = j.createWebClient().createCrumbedUrl("configSubmit").toString();
    Request request = new Request.Builder()
        .url(requestUrl)
        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
        .addHeader("Connection", "keep-alive")
        .addHeader("Cache-Control", "max-age=0")
        .addHeader("Origin", jenkinsUrl)
        .addHeader("Upgrade-Insecure-Requests", "1")
        .addHeader("DNT", "1")
        .addHeader("Referer", j.getURL().toURI().resolve("jenkins/configure").toString())
        .post(rawBody)
        .build();

    Response response = client.newCall(request).execute();
    assertThat(response.code(), is(lessThan(400)));

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

    // When: we run a build
    FreeStyleBuild build = j.buildAndAssertSuccess(project);

    // Then it finishes successfully and the logs contain our command.
    assertThat(j.getLog(build), containsString("echo Hello"));
  }
}
