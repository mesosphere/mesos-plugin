package org.jenkinsci.plugins.mesos.fixture;

import hudson.model.Node.Mode;
import hudson.model.labels.LabelAtom;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate;

/**
 * A Mother object for {@link org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate}.
 *
 * @see <a href="https://martinfowler.com/bliki/ObjectMother.html">ObjectMother</a>
 */
public class AgentSpecMother {

  public static MesosAgentSpecTemplate simple =
    new MesosAgentSpecTemplate(
            "label",
            Mode.EXCLUSIVE,
            "0.1",
            "32",
            "1",
            true,
            "1",
            "1",
            "0",
            "0",
            "",
            "",
            "",
            "",
            "",
            "",
            "");
}
