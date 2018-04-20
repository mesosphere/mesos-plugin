package org.jenkinsci.plugins.mesos;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import hudson.model.TaskListener;
import jenkins.metrics.api.Metrics;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.PrintStream;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Mesos.class, Metrics.class})
public class MesosComputerLauncherTest {
    @Mock
    private MesosCloud mesosCloud;

    private Mesos mesos;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        PowerMockito.mockStatic(Mesos.class);
        PowerMockito.mockStatic(Metrics.class);

        mesos = Mockito.mock(Mesos.class);
        MetricRegistry metricRegistry = Mockito.mock(MetricRegistry.class, Mockito.RETURNS_DEEP_STUBS);
        Timer.Context  timerContext   = Mockito.mock(Timer.Context.class);

        Mockito.when(mesosCloud.getRole()).thenReturn("root");
        Mockito.when(mesos.isSchedulerRunning()).thenReturn(true);
        Mockito.when(Mesos.getInstance(Matchers.any())).thenReturn(mesos);
        Mockito.when(Metrics.metricRegistry()).thenReturn(metricRegistry);
        Mockito.when(metricRegistry.timer(Matchers.anyString()).time())
                .thenReturn(timerContext);
    }


    /**
     * The launch method can enter an infinite loop when a computer is
     * coming online, fails, and never connects. This test exercises those
     * conditions and ensures the method exits as expected.
     */
    @Test
    public void testLaunchExitsOnFailure() throws InterruptedException {
        final long     startTime      = System.currentTimeMillis();
        MesosComputer  mesosComputer  = Mockito.mock(MesosComputer.class);
        MesosSlave     mesosSlave     = Mockito.mock(MesosSlave.class, Mockito.RETURNS_DEEP_STUBS);
        MesosSlaveInfo mesosSlaveInfo = Mockito.mock(MesosSlaveInfo.class);
        TaskListener   taskListener   = Mockito.mock(TaskListener.class);
        PrintStream    printStream    = Mockito.mock(PrintStream.class);

        Mockito.when(taskListener.getLogger()).thenReturn(printStream);
        Mockito.when(mesosComputer.getNode()).thenReturn(mesosSlave);
        Mockito.when(mesosComputer.isOffline()).thenReturn(true);
        Mockito.when(mesosComputer.isConnecting()).thenReturn(true);
        Mockito.when(mesosComputer.isOnline()).thenReturn(true);
        Mockito.when(mesosSlave.getCpus()).thenReturn(1.0);
        Mockito.when(mesosSlave.getMem()).thenReturn(32);
        Mockito.when(mesosSlave.getDiskNeeded()).thenReturn(100.0);
        Mockito.when(mesosSlave.getSlaveInfo()).thenReturn(mesosSlaveInfo);
        Mockito.when(mesosSlave.getProvisioningContext().stop())
                .thenReturn(System.currentTimeMillis() - startTime);

        // place to store the SlaveResult object passed on
        // `startJenkinsSlave`
        final Mesos.SlaveResult[] result = new Mesos.SlaveResult[1];

        Mockito.doAnswer(invocation -> {
            final Mesos.SlaveResult slaveResult = (Mesos.SlaveResult) (invocation.getArguments())[1];
            slaveResult.running(null);
            result[0] = slaveResult;
            return null;
        })
                .when(mesos)
                .startJenkinsSlave(Matchers.any(Mesos.SlaveRequest.class),
                        Matchers.any(Mesos.SlaveResult.class));

        MesosComputerLauncher launcher = new MesosComputerLauncher(mesosCloud, "mycloud");
        Thread                t        = new Thread(() -> launcher.launch(mesosComputer, taskListener));
        t.setName("mesos-launcher-thread");
        t.start();
        assert (t.isAlive());
        // need to wait for some things to progress
        Thread.sleep(250L);
        // grab the SlaveResult item from the `startJenkinsSlave` call
        // and use it to mark this launch as failed.
        result[0].failed(null);
        // timer is 5s in launcher, sleep for it
        Thread.sleep(5200L);
        // thread should be over
        assert (!t.isAlive());
    }

}