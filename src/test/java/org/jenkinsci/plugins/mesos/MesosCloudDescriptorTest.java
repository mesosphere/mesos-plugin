package org.jenkinsci.plugins.mesos;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.util.FormValidation.Kind;
import org.jenkinsci.plugins.mesos.MesosCloud.DescriptorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosCloudDescriptorTest {

  @Test
  void validateFrameworkName(TestUtils.JenkinsRule j) throws Exception {
    MesosCloud.DescriptorImpl descriptor = new DescriptorImpl();
    assertThat(descriptor.doCheckFrameworkName("").kind, is(Kind.ERROR));
    assertThat(descriptor.doCheckFrameworkName("something").kind, is(Kind.OK));
  }

  @Test
  void validateAgentUser(TestUtils.JenkinsRule j) throws Exception {
    MesosCloud.DescriptorImpl descriptor = new DescriptorImpl();
    assertThat(descriptor.doCheckAgentUser("").kind, is(Kind.ERROR));
    assertThat(descriptor.doCheckAgentUser("something").kind, is(Kind.OK));
  }
}
