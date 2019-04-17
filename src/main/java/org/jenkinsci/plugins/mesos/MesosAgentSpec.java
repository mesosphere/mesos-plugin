package org.jenkinsci.plugins.mesos;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import java.util.Set;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * This is the Mesos agent pod spec config set by a user.
 */
public class MesosAgentSpec extends AbstractDescribableImpl<MesosAgentSpec> {

  final private String label;
  final private Set<LabelAtom> labelSet;

  @DataBoundConstructor
  public MesosAgentSpec(String label) {
    this.label = label;
    this.labelSet = Label.parse(label);
  }

  @Extension
  public static final class DescriptorImpl extends Descriptor<MesosAgentSpec> {

    public DescriptorImpl() {
      load();
    }
  }

  // Getters

  public String getLabel() {
    return this.label;
  }

  public Set<LabelAtom> getLabelSet() {
    return this.labelSet;
  }
}
