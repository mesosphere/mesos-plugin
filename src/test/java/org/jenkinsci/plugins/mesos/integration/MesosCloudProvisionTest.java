package org.jenkinsci.plugins.mesos.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
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
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import jenkins.model.Jenkins;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jenkinsci.plugins.mesos.JenkinsConfigForm;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate;
import org.jenkinsci.plugins.mesos.MesosCloud;
import org.jenkinsci.plugins.mesos.MesosJenkinsAgent;
import org.jenkinsci.plugins.mesos.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

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

    final String jsonObject =
        Json.createObjectBuilder()
            .add("system_message", "")
            .add(
                "jenkins-model-MasterBuildConfiguration",
                Json.createObjectBuilder()
                    .add("numExecutors", "2")
                    .add("labelString", "")
                    .add("mode", "NORMAL")
                    .build())
            .add(
                "jenkins-model-GlobalQuietPeriodConfiguration",
                Json.createObjectBuilder().add("quietPeriod", "5").build())
            .add(
                "jenkins-model-GlobalSCMRetryCountConfiguration",
                Json.createObjectBuilder().add("scmCheckoutRetryCount", "0").build())
            .add(
                "jenkins-model-GlobalProjectNamingStrategyConfiguration",
                Json.createObjectBuilder().build())
            .add(
                "jenkins-model-GlobalNodePropertiesConfiguration",
                Json.createObjectBuilder()
                    .add("globalNodeProperties", Json.createObjectBuilder().build())
                    .build())
            .add(
                "hudson-model-UsageStatistics",
                Json.createObjectBuilder()
                    .add("usageStatisticsCollected", Json.createObjectBuilder().build())
                    .build())
            .add(
                "jenkins-management-AdministrativeMonitorsConfiguration",
                Json.createObjectBuilder()
                    .add(
                        "administrativeMonitor",
                        Json.createArrayBuilder()
                            .add("hudson.PluginManager$PluginCycleDependenciesMonitor")
                            .add("hudson.PluginManager$PluginUpdateMonitor")
                            .add("hudson.PluginWrapper$PluginWrapperAdministrativeMonitor")
                            .add("hudsonHomeIsFull")
                            .add("hudson.diagnosis.NullIdDescriptorMonitor")
                            .add("OldData")
                            .add("hudson.diagnosis.ReverseProxySetupMonitor")
                            .add("hudson.diagnosis.TooManyJobsButNoView")
                            .add("hudson.model.UpdateCenter$CoreUpdateMonitor")
                            .add("hudson.node_monitors.MonitorMarkedNodeOffline")
                            .add("hudson.triggers.SCMTrigger$AdministrativeMonitorImpl")
                            .add("jenkins.CLI")
                            .add("jenkins.diagnosis.HsErrPidList")
                            .add("jenkins.diagnostics.CompletedInitializationMonitor")
                            .add("jenkins.diagnostics.RootUrlNotSetMonitor")
                            .add("jenkins.diagnostics.SecurityIsOffMonitor")
                            .add("jenkins.diagnostics.URICheckEncodingMonitor")
                            .add("jenkins.model.DownloadSettings$Warning")
                            .add("jenkins.model.Jenkins$EnforceSlaveAgentPortAdministrativeMonitor")
                            .add("jenkins.security.RekeySecretAdminMonitor")
                            .add("jenkins.security.UpdateSiteWarningsMonitor")
                            .add(
                                "jenkins.security.apitoken.ApiTokenPropertyDisabledDefaultAdministrativeMonitor")
                            .add(
                                "jenkins.security.apitoken.ApiTokenPropertyEnabledNewLegacyAdministrativeMonitor")
                            .add("legacyApiToken")
                            .add("jenkins.security.csrf.CSRFAdministrativeMonitor")
                            .add("slaveToMasterAccessControl")
                            .add("jenkins.security.s2m.MasterKillSwitchWarning")
                            .add("jenkins.slaves.DeprecatedAgentProtocolMonitor")
                            .build())
                    .build())
            .add(
                "jenkins-model-JenkinsLocationConfiguration",
                Json.createObjectBuilder()
                    .add("url", jenkinsUrl)
                    .add("adminAddress", "Adresse nicht konfiguriert <nobody@nowhere>")
                    .build())
            .add("hudson-task-Shell", Json.createObjectBuilder().add("shell", "").build())
            .add(
                "jenkins-model-GlobalCloudConfiguration",
                Json.createObjectBuilder()
                    .add(
                        "cloud",
                        Json.createObjectBuilder()
                            .add("mesosMasterUrl", mesosCluster.getMesosUrl())
                            .add("frameworkName", "Jenkins Scheduler")
                            .add("role", "*")
                            .add("agentUser", System.getProperty("user.name"))
                            .add("jenkinsUrl", jenkinsUrl)
                            .add(
                                "mesosAgentSpecTemplates",
                                Json.createObjectBuilder()
                                    .add("label", "mesos")
                                    .add("mode", "EXCLUSIVE")
                                    .build())
                            .add("stapler-class", "org.jenkinsci.plugins.mesos.MesosCloud")
                            .add("$class", "org.jenkinsci.plugins.mesos.MesosCloud")
                            .build())
                    .build())
            .add("core:apply", "")
            .build()
            .toString();
    final String data =
        new JenkinsConfigForm.Builder()
            .add("system_message", "")
            .add("_.numExecutors", "2")
            .add("_.labelString", "")
            .add("master.mode", "NORMAL")
            .add("_.quietPeriod", "5")
            .add("_.scmCheckoutRetryCount", "0")
            .add(
                "stapler-class", "jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy")
            .add("$class", "jenkins.model.ProjectNamingStrategy$PatternProjectNamingStrategy")
            .add("_.namePattern", ".*")
            .add("_.description", "")
            .add("namingStrategy", "1")
            .add(
                "stapler-class", "jenkins.model.ProjectNamingStrategy$DefaultProjectNamingStrategy")
            .add("$class", "jenkins.model.ProjectNamingStrategy$DefaultProjectNamingStrategy")
            .add("_.usageStatisticsCollected", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("administrativeMonitor", "on")
            .add("_.url", jenkinsUrl)
            .add("_.adminAddress", "Adresse nicht konfiguriert <nobody@nowhere>")
            .add("_.shell", "")
            .add("_.mesosMasterUrl", mesosCluster.getMesosUrl())
            .add("_.frameworkName", "Jenkins Scheduler")
            .add("_.role", "*")
            .add("_.agentUser", System.getProperty("user.name"))
            .add("_.jenkinsUrl", jenkinsUrl)
            .add("_.label", "mesos")
            .add("mode", "EXCLUSIVE")
            .add("stapler-class", "org.jenkinsci.plugins.mesos.MesosCloud")
            .add("$class", "org.jenkinsci.plugins.mesos.MesosCloud")
            .add("core:apply", "")
            .add("json", jsonObject)
            .build();

    final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");
    RequestBody rawBody = RequestBody.create(FORM, data);

    final String requestUrl = j.createWebClient().createCrumbedUrl("configSubmit").toString();
    Request request =
        new Request.Builder()
            .url(requestUrl)
            .addHeader(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
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
