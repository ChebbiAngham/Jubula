/*******************************************************************************
 * Copyright (c) 2004, 2010 BREDEX GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BREDEX GmbH - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jubula.app.cmd.batch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jubula.app.cmd.i18n.Messages;
import org.eclipse.jubula.client.cmd.AbstractCmdlineClient;
import org.eclipse.jubula.client.cmd.batch.ProgressController;
import org.eclipse.jubula.client.cmd.controller.IClcServer;
import org.eclipse.jubula.client.cmd.controller.intern.RmiBase;
import org.eclipse.jubula.client.core.AUTEvent;
import org.eclipse.jubula.client.core.AUTServerEvent;
import org.eclipse.jubula.client.core.AutStarterEvent;
import org.eclipse.jubula.client.core.ClientTestFactory;
import org.eclipse.jubula.client.core.IAUTEventListener;
import org.eclipse.jubula.client.core.IAUTServerEventListener;
import org.eclipse.jubula.client.core.IClientTest;
import org.eclipse.jubula.client.core.IServerEventListener;
import org.eclipse.jubula.client.core.ServerEvent;
import org.eclipse.jubula.client.core.agent.AutAgentRegistration;
import org.eclipse.jubula.client.core.agent.AutRegistrationEvent;
import org.eclipse.jubula.client.core.agent.IAutRegistrationListener;
import org.eclipse.jubula.client.core.agent.AutRegistrationEvent.RegistrationStatus;
import org.eclipse.jubula.client.core.businessprocess.CompletenessGuard;
import org.eclipse.jubula.client.core.businessprocess.ExternalTestDataBP;
import org.eclipse.jubula.client.core.businessprocess.ITestExecutionEventListener;
import org.eclipse.jubula.client.core.businessprocess.JobConfiguration;
import org.eclipse.jubula.client.core.businessprocess.TestExecution;
import org.eclipse.jubula.client.core.businessprocess.TestExecutionEvent;
import org.eclipse.jubula.client.core.businessprocess.TestResultBP;
import org.eclipse.jubula.client.core.communication.ConnectionException;
import org.eclipse.jubula.client.core.communication.ServerConnection;
import org.eclipse.jubula.client.core.model.IAUTConfigPO;
import org.eclipse.jubula.client.core.model.IAUTMainPO;
import org.eclipse.jubula.client.core.model.ICapPO;
import org.eclipse.jubula.client.core.model.IEventExecTestCasePO;
import org.eclipse.jubula.client.core.model.IExecStackModificationListener;
import org.eclipse.jubula.client.core.model.IExecTestCasePO;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IProjectPO;
import org.eclipse.jubula.client.core.model.ITestSuitePO;
import org.eclipse.jubula.client.core.model.ReentryProperty;
import org.eclipse.jubula.client.core.persistence.GeneralStorage;
import org.eclipse.jubula.client.core.persistence.Hibernator;
import org.eclipse.jubula.client.core.persistence.ProjectPM;
import org.eclipse.jubula.client.core.utils.ProgressEventDispatcher;
import org.eclipse.jubula.toolkit.common.exception.ToolkitPluginException;
import org.eclipse.jubula.tools.constants.AutConfigConstants;
import org.eclipse.jubula.tools.constants.StringConstants;
import org.eclipse.jubula.tools.exception.CommunicationException;
import org.eclipse.jubula.tools.exception.JBException;
import org.eclipse.jubula.tools.i18n.I18n;
import org.eclipse.jubula.tools.messagehandling.MessageIDs;
import org.eclipse.jubula.tools.registration.AutIdentifier;
import org.eclipse.jubula.tools.utils.EnvironmentUtils;
import org.eclipse.jubula.tools.utils.FileUtils;
import org.eclipse.osgi.util.NLS;


/**
 * This controller offers methods to create batch jobs from file
 * and to execute jobs.
 *
 * @author BREDEX GmbH
 * @created Mar 29, 2006
 */
public class ExecutionController implements IAUTServerEventListener,
        IServerEventListener, IAUTEventListener, ITestExecutionEventListener, 
        IAutRegistrationListener {
    /**
     * @author BREDEX GmbH
     * @created Oct 13, 2010
     */
    private class ClcService implements IClcServer {

        /** indicates if a Test Suite is running */
        private boolean m_tsRunning;
        /** result for caller */
        private int m_result;

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public int runTestSuite(String tsName, int timeout, 
                Map<String, String> variables) {
            m_tsRunning = false;
            m_stopProcessing = false; // bugfix for ticket #3501: 1+n test
                                      // execution is otherwise not correctly
                                      // synchronized for the clcserver
            setNoErrorWhileExecution(true); // bugfix for ticket #3501: m_result
                                            // is otherwise not resettet
            WatchdogTimer timer = null;
            if (timeout > 0) {
                timer = new WatchdogTimer(timeout);
            }
            m_result = 0;
            IProjectPO project = m_job.getProject();
            ITestSuitePO workUnit = null;
            for (ITestSuitePO ts
                : project.getTestSuiteCont().getTestSuiteList()) {
                if (ts.getName().equals(tsName)) {
                    workUnit = ts;
                    break;
                }
            }
            if (workUnit == null) {
                m_result = -1;
            } else {
                ClientTestFactory.getClientTest().startTestSuite(
                        workUnit,
                        m_job.getLanguage(),
                        m_startedAutId,
                        m_job.isAutoScreenshot(),
                        variables);
                m_tsRunning = true;
                timer.start();
            }
            while (!m_stopProcessing && m_tsRunning) {
                synchronized (m_rmiBase) {
                    try {
                        m_rmiBase.wait();
                    } catch (InterruptedException e) {
                        // just check
                    }
                }
            }
            if (timer != null) {
                timer.abort();
            }
            return m_result;
        }
        
        /**
         * Notfiy the client that the TS has completed
         * @param result result of the TS run
         */
        @SuppressWarnings("synthetic-access")
        public void tsDone(int result) {
            m_result = result;
            m_tsRunning = false;
            if (m_rmiBase != null) {
                synchronized (m_rmiBase) {
                    m_rmiBase.notifyAll();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public void shutdown() {
            m_clientActive = false;
            stopProcessing();
        }

    }

    /**
     * @author BREDEX GmbH
     * @created Oct 16, 2009
     */
    private final class WatchdogTimer extends Thread {

        /** when should the run be finished? */
        private long m_stoptime;
        
        /** should the time stop */
        private boolean m_abort = false;
        
        /**
         * @param timeout Time in seconds the watchdog should wait before
         * aborting the run.
         */
        public WatchdogTimer(int timeout) {
            super(Messages.WatchdogTimer);
            setDaemon(true);
            m_stoptime = new Date().getTime();
            m_stoptime += timeout * 1000;
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        @Override
        public void run() {
            do {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore, check ist done at end of loop
                }    
                if (m_abort) {
                    return;
                }
            } while (new Date().getTime() < m_stoptime);

            AbstractCmdlineClient.printConsoleLn(
                    Messages.ExecutionControllerAbort, true);

            ClientTestFactory.getClientTest().stopTestExecution();
            stopProcessing();
            
            // wait 30 seconds, then exit the whole program
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                // ignore, check ist done at end of loop
            }   
            if (!m_abort) {
                System.exit(1);
            }
        }

        /**
         * abort this watchdog
         */
        public void abort() {
            m_abort = true;
            this.interrupt();
        }
    }

    /** 
     * Name of the environment variable that defines the time the client should
     * wait during AUT startup process.
     */
    private static final String AUT_STARTUP_DELAY_VAR = "GD_AUT_STARTUP_DELAY"; //$NON-NLS-1$
    
    /**
     * default time to wait during startup process 
     */
    private static final int AUT_STARTUP_DELAY_DEFAULT = 5000;
    
    /** the logger */
    private static final Log LOG = LogFactory.getLog(ExecutionController.class);
    
    /** instance of controller */
    private static ExecutionController instance;

    /** configuration of desired job */
    private JobConfiguration m_job;
    
    /** true if client is processing a job */
    private boolean m_idle = false;

    /** true if this is the first time the AUT is being started for the current test suite */
    private boolean m_isFirstAutStart = true;
    
    /** true if test should be reported as successful */
    private boolean m_noErrorWhileExecution = true;
    
    /** true if client sends a shutdown command to end test execution */
    private boolean m_shutdown = false;

    /** 
     * true if fatal error occured, and processing of the batch process must
     * be stopped
     */
    private boolean m_stopProcessing = false;
    
    /** process for watching test execution */
    private TestExecutionWatcher m_progress = new TestExecutionWatcher();
    /** controller to listen to db connection progress */
    private ProgressController m_progressController = new ProgressController();

    /** the ID of the AUT that was started for test execution */
    private AutIdentifier m_startedAutId = null;
    
    /** the RMI service registry */
    private RmiBase m_rmiBase;
    /** the implemetation of the service for internal use */
    @SuppressWarnings("synthetic-access")
    private final ClcService m_clcServiceImpl = new ClcService();
    /** is there a CLC client connection */
    private boolean m_clientActive = false;

    /** private constructor */
    private ExecutionController() {
        IClientTest clientTest = ClientTestFactory.getClientTest();
        clientTest.addAUTServerEventListener(this);
        clientTest.addAutStarterEventListener(this);
        clientTest.addTestEventListener(this);
        clientTest.addTestExecutionEventListener(this);
        AutAgentRegistration.getInstance().addListener(this);
    }
    
    /**
     * Method to get the single instance of this class.
     * @return the instance of this Singleton
     */
    public static ExecutionController getInstance() {
        if (instance == null) {
            instance = new ExecutionController();
        }
        return instance;
    }
    
    /**
     * creates the job passend to command Line client
     * @param configFile File
     * @throws IOException Error
     * @return Jobconfiguration
     */
    public JobConfiguration initJob(File configFile) throws IOException {
        if (configFile != null) {
            // Create JobConfiguration from xml
            BufferedReader in = null;
            StringWriter writer = new StringWriter();
            try {
                in = new BufferedReader(new FileReader(configFile));
                String line = null;
                while ((line = in.readLine()) != null) {
                    writer.write(line);
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
            String xml = writer.toString();    
            m_job = JobConfiguration.readFromXML(xml);
        } else {
            // or create an emty JobConfiguration
            m_job = new JobConfiguration();
        }
        return m_job;
    }
    
    /**
     * only loads projects and do a completeness check
     */
    public void simulateJob() {
        prepareExecution();
    }
    
    /**
     * executes the complete test
     * @throws CommunicationException Error
     * @return boolean true if all testsuites completed successfully
     */
    public boolean executeJob() throws CommunicationException {
        // start the watchdog timer
        WatchdogTimer timer = null;
        if (m_job.getTimeout() > 0) {        
            timer = new WatchdogTimer(m_job.getTimeout());
            timer.start();
        }
        // init ClientTest
        IClientTest clientTest = ClientTestFactory.getClientTest();
        clientTest.connectToServer(m_job.getServer(), m_job.getPort());
        if (!ServerConnection.getInstance().isConnected()) {
            throw new CommunicationException(Messages.ConnectionToAUT_Agent,
                    MessageIDs.E_COMMUNICATOR_CONNECTION);
        }
        clientTest.setRelevantFlag(m_job.isRelevant());
        prepareExecution();
        // start aut, working will be set false, after aut started
        m_idle = true;
        // ends testexecution if shutdown command was received from the client
        if (m_shutdown) {
            AbstractCmdlineClient.printConsoleLn(Messages
                    .ReceivedShutdownCommand , true);
            endTestExecution();
        }
        try {
            
            if (m_rmiBase != null) { // run as a CLS server
                doClcService();
            } else if (m_job.getTestJob() != null) {
                ensureAutIsStarted(m_job.getActualTestSuite(), 
                        m_job.getAutConfig());
                doSingleJob();
            } else {
                ensureAutIsStarted(m_job.getActualTestSuite(), 
                        m_job.getAutConfig());
                doTestSuites();
            }
        } catch (ToolkitPluginException e1) {
            AbstractCmdlineClient.printConsoleError(
                    Messages.ExecutionControllerAUT
                      + Messages.ErrorMessageAUT_TOOLKIT_NOT_AVAILABLE);
        }
        if (timer != null) {
            timer.abort();
        }
        
        return isNoErrorWhileExecution();
    }

    /**
     * executing batch of test suites
     */
    private void doTestSuites() {
        // executing batch of test suites
        while (m_job.getActualTestSuite() != null 
                && !m_stopProcessing) {
            while (m_idle && !m_stopProcessing) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            if (m_job.getActualTestSuite() != null 
                    && !m_stopProcessing
                    && !m_idle && !m_isFirstAutStart) {
   
                m_idle = true;
                AbstractCmdlineClient.printConsoleLn(StringConstants.TAB
                        + NLS.bind(Messages
                                .ExecutionControllerTestSuiteBegin, 
                                new Object[] {m_job.getActualTestSuite()
                                    .getName()}) 
                        + StringConstants.LEFT_PARENTHESES 
                        + (m_job.getActualTestSuiteIndex() + 1) 
                        + StringConstants.SLASH 
                        + m_job.getJobSize() 
                        + StringConstants.RIGHT_PARENTHESES, 
                        true); 
                ClientTestFactory.getClientTest().startTestSuite(
                        m_job.getActualTestSuite(),
                        m_job.getLanguage(),
                        m_startedAutId != null ? m_startedAutId : m_job
                                .getAutId(), m_job.isAutoScreenshot());
            } 
        }
    }

    /**
     * run a test job
     */
    private void doSingleJob() {
        AbstractCmdlineClient.printConsoleLn(
                StringConstants.TAB 
                + NLS.bind(Messages.ExecutionControllerTestJobBegin,
                        new Object[] {m_job.getTestJob().getName()}), 
                true);
        ClientTestFactory.getClientTest().startTestJob(
                m_job.getTestJob(), m_job.getLanguage(),
                m_job.isAutoScreenshot());
    }

    /**
     * wait for the CLC service to receive a shutdown from the client
     */
    private void doClcService() throws ToolkitPluginException {
        IProjectPO project = m_job.getProject();
        String autConfigName = m_job.getAutConfigName();
        
        ITestSuitePO workUnit = null;
        IAUTConfigPO autConfig = null;
        
        for (ITestSuitePO ts : project.getTestSuiteCont().getTestSuiteList()) {
            for (IAUTConfigPO cfg : ts.getAut().getAutConfigSet()) {
                if (autConfigName.equals(cfg.getName())) {
                    workUnit = ts;
                    autConfig = cfg;
                    break;
                }
            }
        }
        if (workUnit != null && autConfig != null) {
            ensureAutIsStarted(workUnit, autConfig);
        }
        m_clientActive = true;
        do {
            synchronized (m_rmiBase) {
                try {
                    m_rmiBase.wait();
                } catch (InterruptedException e) {
                    // just check if we should stop
                }
            }
        } while (m_clientActive);
        if (autConfig != null) {
            try {
                AutIdentifier startedAutId = new AutIdentifier(
                        autConfig.getConfigMap().get(
                                AutConfigConstants.AUT_ID));
                if (ServerConnection.getInstance().isConnected()) {
                    ClientTestFactory.getClientTest().stopAut(startedAutId);
                }
            } catch (ConnectionException e) {
                LOG.info(Messages.ErrorWhileStoppingAUT, e);
            }
        }

    }
        
    /**
     * end processing and notify any waiting CLC service threads
     */
    private void stopProcessing() {
        m_stopProcessing = true;
        if (m_rmiBase != null) {
            synchronized (m_rmiBase) {
                m_rmiBase.notifyAll();
            }
        }
    }
    /**
     * prepares the test execution by:
     *   <p> * initializing database connection
     *   <p> * loading the project
     *   <p> * validating project
     */
    private void prepareExecution() {
        // setting LogDir , resource/html must be in classpath
        setLogDir();
        // set data dir for external data
        ExternalTestDataBP.setDataDir(new File(m_job.getDataDir()));
        // init Hibernator
        // Hibernate.properties and mapping files
        // have to be in classpath
        ProgressEventDispatcher.addProgressListener(m_progressController);
        Hibernator.setSchemaName(m_job.getDbscheme());
        Hibernator.setUser(m_job.getDbuser());
        Hibernator.setPw(m_job.getDbpw());
        Hibernator.setUrl(m_job.getDb());
        if (!Hibernator.init()) {
            throw new IllegalArgumentException(Messages.
                    ExecutionControllerInvalidDBDataError, null);
        }
        // load project
        loadProject();
    }

    /**
     * sets the log Directory
     */
    private void setLogDir() {
        if (m_job.getResultDir() != null 
            && m_job.getResultDir().length() != 0) {
            Validate.isTrue(FileUtils.isValidPath(m_job.getResultDir()), 
                    Messages.ExecutionControllerLogPathError);
            ClassLoader cl = ClientTestFactory.class.getClassLoader();
            URL formatUrl = TestResultBP.getInstance().getXslFileURL();
            URL cssUrl = cl.getResource("reportStyle.css"); //$NON-NLS-1$
            if (cssUrl != null
                && formatUrl != null) {
                try {
                    File cssFile = 
                        new File(FileLocator.resolve(cssUrl).getFile());
                    String htmlDir = cssFile.getParentFile().getAbsolutePath();
                    String logDir = m_job.getResultDir();
                    ClientTestFactory.getClientTest().setLogPath(
                        logDir, 
                        formatUrl, 
                        htmlDir);
                } catch (IOException ioe) {
                    LOG.error(Messages.ErrorWhileInitializingTestResult, ioe);
                }
            }
        }
    }

    /**
     * starts the aut
     * @param ts the Test Suite which will be started
     * @param autConf configuration for this AUT
     */
    private void ensureAutIsStarted(ITestSuitePO ts, IAUTConfigPO autConf) 
        throws ToolkitPluginException {
        if (ts != null && autConf != null) {
            final IAUTMainPO aut = ts.getAut();
            AbstractCmdlineClient.printConsoleLn(Messages.ExecutionControllerAUT
                    + NLS.bind(Messages.ExecutionControllerAUTStart, 
                            new Object[] {aut.getName()}), true); 
            
            if (ts != null) {
                AutIdentifier autToStart = new AutIdentifier(
                        autConf.getConfigMap().get(
                                AutConfigConstants.AUT_ID));
                AUTStartListener asl = new AUTStartListener(autToStart);
                ClientTestFactory.getClientTest().addTestEventListener(asl);
                ClientTestFactory.getClientTest().addAUTServerEventListener(
                        asl);
                AutAgentRegistration.getInstance().addListener(asl);
                ClientTestFactory.getClientTest().startAut(aut,
                        autConf, m_job.getLanguage());
                m_startedAutId = autToStart;
                while (!asl.autStarted() && !asl.hasAutStartFailed()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                waitExternalTime();
            }
        } else {
            // assume that the aut has already been started via e.g. gdrun
            m_idle = false;
            m_isFirstAutStart = false;
        }
    }

    /**
     * this method delays the test execution start during AUT startup
     */
    private void waitExternalTime() {
        int timeToWait = AUT_STARTUP_DELAY_DEFAULT;
        try {
            String value = EnvironmentUtils.getProcessEnvironment()
                    .getProperty(AUT_STARTUP_DELAY_VAR);
            if (value == null) {
                value = System.getProperty(AUT_STARTUP_DELAY_VAR);
            }

            timeToWait = Integer.valueOf(value).intValue();
        } catch (NumberFormatException e) {
            // ignore invalid formatted values and use default instead
        }
        try {
            Thread.sleep(timeToWait);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * @author BREDEX GmbH
     * @created 24.08.2009
     */
    public class AUTStartListener implements IAUTEventListener, 
        IAUTServerEventListener, IAutRegistrationListener {
        /** flag to indicate that the aut has been successfully started */
        private boolean m_autStarted = false;
        
        /** flag to indicate that the aut start has failed*/
        private boolean m_autStartFailed = false;
        
        /** timer to set autStartFailed to true after a certain amount of time */
        private Timer m_startFailedTimer = new Timer();
        
        /** startup timeout: 5 minutes */
        private long m_autStartTimeout = 5 * 60 * 1000;

        /** ID of the AUT that should be started */
        private AutIdentifier m_autToStart;
        
        /** 
         * Constructor
         * 
         * @param autToStart ID of the AUT that should be started.
         */
        public AUTStartListener(AutIdentifier autToStart) {
            m_autToStart = autToStart;
            m_startFailedTimer.schedule(new TimerTask() {
                public void run() {
                    setAutStartFailed(true);
                    removeListener();
                }
            }, m_autStartTimeout);
        }
        
        /** @return the hasBeenNotified */
        public synchronized boolean autStarted() {
            return m_autStarted;
        }

        /** {@inheritDoc} */
        public synchronized void stateChanged(AUTEvent event) {
            switch (event.getState()) {
                case AUTEvent.AUT_STARTED:
                    m_autStarted = true;
                    dispose();
                    break;
                default:
                    break;
            }
        }

        /** @return the autStartFailed */
        public synchronized boolean hasAutStartFailed() {
            return m_autStartFailed;
        }

        /** {@inheritDoc} */
        public void stateChanged(AUTServerEvent event) {
            switch (event.getState()) {
                case AUTServerEvent.COMMUNICATION:
                case AUTServerEvent.COULD_NOT_ACCEPTING:
                case AUTServerEvent.DOTNET_INSTALL_INVALID:
                case AUTServerEvent.INVALID_JAR:
                case AUTServerEvent.INVALID_JAVA:
                case AUTServerEvent.JDK_INVALID:
                case AUTServerEvent.NO_MAIN_IN_JAR:
                case AUTServerEvent.SERVER_NOT_INSTANTIATED:
                case ServerEvent.CONNECTION_CLOSED:
                    setAutStartFailed(true);
                    dispose();
                    break;
                default:
                    break;
            }
        }

        /**
         * @param autStartFailed the autStartFailed to set
         */
        protected synchronized void setAutStartFailed(boolean autStartFailed) {
            m_autStartFailed = autStartFailed;
        }
        
        /** dispose this listener and stop all running tasks */
        private void dispose () {
            m_startFailedTimer.cancel();
            removeListener();
        }
        
        /** remove listener */
        protected void removeListener() {
            ClientTestFactory.getClientTest()
                    .removeTestEventListener(this);
            ClientTestFactory.getClientTest()
                    .removeAUTServerEventListener(this);
            AutAgentRegistration.getInstance().removeListener(this);
        }

        /**
         * {@inheritDoc}
         */
        public void handleAutRegistration(AutRegistrationEvent event) {
            if (event.getAutId().equals(m_autToStart)
                    && event.getStatus() == RegistrationStatus.Register) {
                m_autStarted = true;
                dispose();
            }
        }
    }
    
    /**
     * loads a project
     */
    private void loadProject() {
        AbstractCmdlineClient.printConsoleLn(Messages
                .ExecutionControllerDatabase
            + NLS.bind(Messages.ExecutionControllerLoadingProject, 
                    new Object[] {m_job.getProjectName()}), true);
        try {
            IProjectPO actualProject = 
                ProjectPM.loadProjectByNameAndVersion(m_job.getProjectName(), 
                    m_job.getProjectMajor(), m_job.getProjectMinor());
            if (actualProject != null) {
                ProjectPM.loadProjectInROSession(actualProject);
                final IProjectPO currentProject = GeneralStorage.getInstance()
                    .getProject();
                m_job.setProject(currentProject);
                AbstractCmdlineClient.printConsoleLn(
                        Messages.ExecutionControllerDatabase
                    + NLS.bind(Messages.ExecutionControllerProjectLoaded, 
                            new Object[] {m_job.getProjectName()}), true);
            }
        } catch (JBException e1) { // NOPMD by zeb on 10.04.07 14:47
            /* An exception was thrown while loading data or closing a session
             * using Hibernate. The project is never set. This is detected
             * during job validation (initAndValidate). */
        }
        m_job.initAndValidate();
        AbstractCmdlineClient.printConsoleLn(Messages
                .ExecutionControllerProjectCompleteness, true);
        CompletenessGuard.checkAll(m_job.getLanguage(), m_job.getProject());
        List<String> suitesWithIncompleteOM = new LinkedList<String>();
        List<String> suitesWithIncompleteTD = new LinkedList<String>();
        List<String> suitesWithMissingSpecTc = new LinkedList<String>();
        for (ITestSuitePO ts : m_job.getTestSuites()) {
            if (!ts.getSumSpecTcFlag()) {
                suitesWithMissingSpecTc.add(
                    NLS.bind(Messages.ExecutionControllerCheckSpecTc, 
                            new Object[] {ts.getName()}));
                AbstractCmdlineClient.printConsoleLn(NLS.bind(
                        Messages.ExecutionControllerCheckSpecTc, 
                        new Object[] {ts.getName()}), true);
            }
            if (!ts.getSumOMFlag(ts.getAut())) {
                suitesWithIncompleteOM.add(
                    NLS.bind(Messages.ExecutionControllerCheckOM, 
                            new Object[] {ts.getName()}));
                AbstractCmdlineClient.printConsoleLn(
                    NLS.bind(Messages.ExecutionControllerCheckOM, 
                            new Object[] {ts.getName()}), true); 
            }
            if (!ts.getSumTdFlag(m_job.getLanguage())) {
                suitesWithIncompleteTD.add(
                    NLS.bind(Messages.ExecutionControllerCheckTD, 
                            new Object[] {ts.getName()}));
                AbstractCmdlineClient.printConsoleLn(
                    NLS.bind(Messages.ExecutionControllerCheckTD, 
                            new Object[] {ts.getName()}), true); 
            }
        }
        StringBuilder sb = new StringBuilder(
                Messages.ExecutionControllerCheckError);
        sb.append(StringConstants.COLON);
        for (String suiteName : suitesWithIncompleteOM) {
            sb.append(StringConstants.NEWLINE + StringConstants.TAB);
            sb.append(suiteName);
            sb.append(StringConstants.DOT + StringConstants.SPACE);
        }
        for (String suiteName : suitesWithIncompleteTD) {
            sb.append(StringConstants.NEWLINE + StringConstants.TAB);
            sb.append(suiteName);
            sb.append(StringConstants.DOT + StringConstants.SPACE);
        }
        Validate.isTrue(
            suitesWithIncompleteOM.isEmpty() 
            && suitesWithIncompleteTD.isEmpty(), 
            sb.toString());
    }

    /**
     * {@inheritDoc}
     */
    public void stateChanged(AUTServerEvent event) {
        switch (event.getState()) {
            case AUTServerEvent.INVALID_JAR:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerInvalidJarError);
                stopProcessing();
                m_idle = false;
                break;
            case AUTServerEvent.INVALID_JAVA:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerInvalidJREError);
                stopProcessing();
                m_idle = false;
                break;
            case AUTServerEvent.SERVER_NOT_INSTANTIATED:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerServerNotInstantiated);
                stopProcessing();
                m_idle = false;
                break;
            case AUTServerEvent.NO_MAIN_IN_JAR:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerInvalidMainError);
                stopProcessing();
                m_idle = false;
                break;
            case AUTServerEvent.COULD_NOT_ACCEPTING:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerAUTStartError);
                stopProcessing();
                m_idle = false;
                break;
            case ServerEvent.CONNECTION_CLOSED:
                stopProcessing();
                m_idle = false;
                break;
            case AUTServerEvent.DOTNET_INSTALL_INVALID:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerDotNetInstallProblem);
                stopProcessing();
                m_idle = false;
                break;
                
            default:
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stateChanged(AutStarterEvent event) {
        AbstractCmdlineClient.printConsoleLn(NLS.bind(
                Messages.ExecutionControllerServer, 
                new Object[] {event}), true);
        switch (event.getState()) {
            case ServerEvent.CONNECTION_CLOSED:
                break;
            default:
                break;
        }            

    }

    /**
     * {@inheritDoc}
     */
    public void stateChanged(AUTEvent event) {
        switch (event.getState()) {
            case AUTEvent.AUT_STARTED:
                AbstractCmdlineClient.printConsoleLn(Messages
                        .ExecutionControllerAUT
                        + Messages.ExecutionControllerAUTStarted, true); 
                AbstractCmdlineClient.printConsoleLn(Messages
                        .ExecutionControllerTestExecution,
                        true);
                break;
            case AUTEvent.AUT_CLASS_VERSION_ERROR:
            case AUTEvent.AUT_MAIN_NOT_FOUND:
            case AUTEvent.AUT_NOT_FOUND:
            case AUTEvent.AUT_ABORTED:
            case AUTEvent.AUT_START_FAILED:
                AbstractCmdlineClient.printConsoleError(Messages
                        .ExecutionControllerAUTStartError);
                stopProcessing();
                break;
            case AUTEvent.AUT_STOPPED:
                AbstractCmdlineClient.printConsoleLn(Messages
                        .ExecutionControllerAUT
                        + Messages.ExecutionControllerAUTStopped, 
                        true);
                stopProcessing();
                break;
            case AUTEvent.AUT_RESTARTED:
                return;
            default:
                break;
        }
        // generally do not do this, if AUT-Restart-Action is executed!
        if (m_isFirstAutStart) {
            m_idle = false;
            m_isFirstAutStart = false;
        }

    }

    /**
     * {@inheritDoc}
     */
    public void stateChanged(TestExecutionEvent event) {
        if (event.getException() != null
            && event.getException() instanceof JBException) {
            String errorMsg = 
                I18n.getString(event.getException().getMessage(), true);
            AbstractCmdlineClient.printConsoleError(errorMsg);
        }

        switch (event.getState()) {
            case TestExecutionEvent.TEST_EXEC_RESULT_TREE_READY:
                TestExecution.getInstance().getTrav()
                    .addExecStackModificationListener(m_progress);
                break;
            case TestExecutionEvent.TEST_EXEC_START:
            case TestExecutionEvent.TEST_EXEC_RESTART:
                break;
            case TestExecutionEvent.TEST_EXEC_FINISHED:
                AbstractCmdlineClient.printConsoleLn(
                        Messages.ExecutionControllerTestSuiteEnd,
                        true);
                m_job.getNextTestSuite();
                m_clcServiceImpl.tsDone(isNoErrorWhileExecution() ? 0 : 1);
                break;
            case TestExecutionEvent.TEST_EXEC_PAUSED:
                TestExecution.getInstance().pauseExecution(false);
                break;
            case TestExecutionEvent.TEST_EXEC_ERROR:
            case TestExecutionEvent.TEST_EXEC_FAILED:
            case TestExecutionEvent.TEST_EXEC_STOP:
                m_job.getNextTestSuite();
                break;
            default:
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void endTestExecution() {
        m_idle = false;
    }

    /**
     * @author BREDEX GmbH
     * @created Dec 3, 2010
     */
    protected class TestExecutionWatcher 
            implements IExecStackModificationListener {

        /**
         * {@inheritDoc}
         */
        public void stackIncremented(INodePO node) {
            String nodeType = StringConstants.EMPTY;
            if (node instanceof IEventExecTestCasePO) {
                IEventExecTestCasePO evPo = (IEventExecTestCasePO)node;
                if (evPo.getReentryProp() != ReentryProperty.RETRY) {
                    setNoErrorWhileExecution(false);
                }
                nodeType = Messages.EventHandler;
            } else if (node instanceof ITestSuitePO) {
                nodeType = Messages.TestSuite;
            } else if (node instanceof IExecTestCasePO) {
                nodeType = Messages.TestCase;
            }
            
            AbstractCmdlineClient.printConsoleLn(nodeType 
                    + Messages.UtilsSeparator
                + String.valueOf(node.getName()), true);
        }

        /**
         * {@inheritDoc}
         */
        public void stackDecremented() {
            // do nothing
        }

        /**
         * {@inheritDoc}
         */
        public void nextDataSetIteration() {
            // do nothing
        }

        /**
         * {@inheritDoc}
         */
        public void nextCap(ICapPO cap) {
            AbstractCmdlineClient.printConsoleLn(StringConstants.TAB
                    + Messages.Step + Messages.UtilsSeparator
                    + String.valueOf(cap.getName()), true);
        }

        /**
         * {@inheritDoc}
         */
        public void retryCap(ICapPO cap) {
            AbstractCmdlineClient.printConsoleLn(StringConstants.TAB
                    + Messages.RetryStep + Messages.UtilsSeparator
                    + String.valueOf(cap.getName()), true);
        }
    }

    /**
     * @param job the job to set
     */
    public void setJob(JobConfiguration job) {
        m_job = job;
        if (m_job.getServerPort() != null) {
            Integer port = Integer.parseInt(m_job.getServerPort());
            m_rmiBase = new RmiBase(port, m_clcServiceImpl);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void handleAutRegistration(AutRegistrationEvent event) {
        if ((event.getAutId().equals(m_startedAutId)
                || event.getAutId().equals(m_job.getAutId()))
                && event.getStatus() == RegistrationStatus.Register) {
            AbstractCmdlineClient.printConsoleLn(Messages.ExecutionControllerAUT
                    + Messages.ExecutionControllerAUTStarted, true); 
            AbstractCmdlineClient.printConsoleLn(Messages
                    .ExecutionControllerTestExecution, true);
            // generally do not do this, if AUT-Restart-Action is executed!
            if (m_isFirstAutStart) {
                m_idle = false;
                m_isFirstAutStart = false;
            }
        }
    }

    /**
     * @param noErrorWhileExecution the noErrorWhileExecution to set
     */
    protected void setNoErrorWhileExecution(boolean noErrorWhileExecution) {
        m_noErrorWhileExecution = noErrorWhileExecution;
    }

    /**
     * @return the noErrorWhileExecution
     */
    protected boolean isNoErrorWhileExecution() {
        return m_noErrorWhileExecution;
    }
}