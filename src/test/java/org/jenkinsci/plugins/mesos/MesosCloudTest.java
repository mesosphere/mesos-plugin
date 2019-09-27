package org.jenkinsci.plugins.mesos;

import hudson.util.XStream2;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosCloudTest {

  @Test
  void deserializeOldConfig(TestUtils.JenkinsRule j) throws IOException {
    final String oldConfig =
        IOUtils.resourceToString(
            "config_1.x.xml",
            StandardCharsets.UTF_8,
            Thread.currentThread().getContextClassLoader());

    final XStream2 xstream = new XStream2();
    //    MesosAgentSpecTemplate.DescriptorImpl.serilizationAliases();
    MesosCloud cloud = (MesosCloud) xstream.fromXML(oldConfig);
  }
}
