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
package org.eclipse.jubula.client.core.businessprocess;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jubula.client.core.Activator;
import org.eclipse.jubula.client.core.ClientTest;
import org.eclipse.jubula.client.core.ClientTestImpl;
import org.eclipse.jubula.client.core.IClientTest;
import org.eclipse.jubula.client.core.agent.AutAgentRegistration;
import org.eclipse.jubula.client.core.agent.AutRegistrationEvent;
import org.eclipse.jubula.client.core.agent.AutRegistrationEvent.RegistrationStatus;
import org.eclipse.jubula.client.core.agent.IAutRegistrationListener;
import org.eclipse.jubula.client.core.businessprocess.TestExecutionEvent.State;
import org.eclipse.jubula.client.core.commands.DisplayManualTestStepResponseCommand;
import org.eclipse.jubula.client.core.commands.EndTestExecutionResponseCommand;
import org.eclipse.jubula.client.core.communication.AUTConnection;
import org.eclipse.jubula.client.core.constants.TestExecutionConstants;
import org.eclipse.jubula.client.core.events.AUTEvent;
import org.eclipse.jubula.client.core.i18n.Messages;
import org.eclipse.jubula.client.core.model.IAUTConfigPO.ActivationMethod;
import org.eclipse.jubula.client.core.model.IAUTMainPO;
import org.eclipse.jubula.client.core.model.ICapPO;
import org.eclipse.jubula.client.core.model.IEventStackModificationListener;
import org.eclipse.jubula.client.core.model.IExecStackModificationListener;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IObjectMappingPO;
import org.eclipse.jubula.client.core.model.IParamDescriptionPO;
import org.eclipse.jubula.client.core.model.IProjectPO;
import org.eclipse.jubula.client.core.model.ITDManager;
import org.eclipse.jubula.client.core.model.ITestDataPO;
import org.eclipse.jubula.client.core.model.ITestJobPO;
import org.eclipse.jubula.client.core.model.ITestResultSummaryPO;
import org.eclipse.jubula.client.core.model.ITestSuitePO;
import org.eclipse.jubula.client.core.model.LogicComponentNotManagedException;
import org.eclipse.jubula.client.core.model.ReentryProperty;
import org.eclipse.jubula.client.core.model.ResultTreeBuilder;
import org.eclipse.jubula.client.core.model.ResultTreeTracker;
import org.eclipse.jubula.client.core.model.TestResult;
import org.eclipse.jubula.client.core.model.TestResultNode;
import org.eclipse.jubula.client.core.persistence.GeneralStorage;
import org.eclipse.jubula.client.core.persistence.Persistor;
import org.eclipse.jubula.client.core.rc.commands.AbstractPostExecutionCommand;
import org.eclipse.jubula.client.core.rc.commands.IPostExecutionCommand;
import org.eclipse.jubula.client.core.utils.ExecObject;
import org.eclipse.jubula.client.core.utils.ModelParamValueConverter;
import org.eclipse.jubula.client.core.utils.ParamValueConverter;
import org.eclipse.jubula.client.core.utils.Traverser;
import org.eclipse.jubula.client.internal.AutAgentConnection;
import org.eclipse.jubula.client.internal.BaseConnection.NotConnectedException;
import org.eclipse.jubula.client.internal.commands.CAPTestResponseCommand;
import org.eclipse.jubula.client.internal.commands.TakeScreenshotResponseCommand;
import org.eclipse.jubula.client.internal.exceptions.ConnectionException;
import org.eclipse.jubula.communication.internal.ICommand;
import org.eclipse.jubula.communication.internal.message.CAPTestMessage;
import org.eclipse.jubula.communication.internal.message.CAPTestResponseMessage;
import org.eclipse.jubula.communication.internal.message.DisplayManualTestStepMessage;
import org.eclipse.jubula.communication.internal.message.EndTestExecutionMessage;
import org.eclipse.jubula.communication.internal.message.InitTestExecutionMessage;
import org.eclipse.jubula.communication.internal.message.Message;
import org.eclipse.jubula.communication.internal.message.MessageCap;
import org.eclipse.jubula.communication.internal.message.MessageParam;
import org.eclipse.jubula.communication.internal.message.NullMessage;
import org.eclipse.jubula.communication.internal.message.PrepareForShutdownMessage;
import org.eclipse.jubula.communication.internal.message.ResetMonitoringDataMessage;
import org.eclipse.jubula.communication.internal.message.RestartAutMessage;
import org.eclipse.jubula.communication.internal.message.TakeScreenshotMessage;
import org.eclipse.jubula.toolkit.common.businessprocess.ToolkitSupportBP;
import org.eclipse.jubula.toolkit.common.xml.businessprocess.ComponentBuilder;
import org.eclipse.jubula.tools.internal.constants.AutConfigConstants;
import org.eclipse.jubula.tools.internal.constants.MonitoringConstants;
import org.eclipse.jubula.tools.internal.constants.StringConstants;
import org.eclipse.jubula.tools.internal.constants.TimeoutConstants;
import org.eclipse.jubula.tools.internal.constants.TimingConstantsClient;
import org.eclipse.jubula.tools.internal.exception.CommunicationException;
import org.eclipse.jubula.tools.internal.exception.InvalidDataException;
import org.eclipse.jubula.tools.internal.exception.JBException;
import org.eclipse.jubula.tools.internal.messagehandling.MessageIDs;
import org.eclipse.jubula.tools.internal.objects.IComponentIdentifier;
import org.eclipse.jubula.tools.internal.objects.event.EventFactory;
import org.eclipse.jubula.tools.internal.objects.event.TestErrorEvent;
import org.eclipse.jubula.tools.internal.registration.AutIdentifier;
import org.eclipse.jubula.tools.internal.utils.ExternalCommandExecutor;
import org.eclipse.jubula.tools.internal.utils.ExternalCommandExecutor.MonitorTask;
import org.eclipse.jubula.tools.internal.utils.IsAliveThread;
import org.eclipse.jubula.tools.internal.utils.StringParsing;
import org.eclipse.jubula.tools.internal.utils.TimeUtil;
import org.eclipse.jubula.tools.internal.xml.businessmodell.Action;
import org.eclipse.jubula.tools.internal.xml.businessmodell.CompSystem;
import org.eclipse.jubula.tools.internal.xml.businessmodell.Component;
import org.eclipse.jubula.tools.internal.xml.businessmodell.ConcreteComponent;
import org.eclipse.jubula.tools.internal.xml.businessmodell.Param;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class creates the captestmessage with test data and sends this message
 * to the server. Then it waits for an answer of the server and processes the
 * answer. Then it sends the next captestmessage.
 * 
 * @author BREDEX GmbH
 * @created 03.09.2004
 */
public class TestExecution {
    /** the component system timeout key */
    public static final String COMP_SYSTEM_TIMEOUT = "CompSystem.Timeout"; //$NON-NLS-1$
    /** <code>EXIT_CODE_ERROR</code> */
    protected static final int EXIT_CODE_NORUN_OK = 100;
    /** <code>EXIT_CODE_OK</code> */
    protected static final int EXIT_CODE_OK = 0;
    
    /**
     * @author BREDEX GmbH
     * @created Feb 2, 2011
     */
    public enum PauseMode {
        /**
         * <code>TOGGLE</code> between <code>PAUSE</code> and
         * <code>UNPAUSE</code>
         */
        TOGGLE,
        /**
         * <code>PAUSE</code>
         */
        PAUSE,
        /**
         * <code>UNPAUSE</code>
         */
        UNPAUSE,
        /**
         * <code>CONTINUE_WITHOUT_EH</code> continue test execution without
         * executing the next event handler
         */
        CONTINUE_WITHOUT_EH
    }
    /** The logger */
    private static final Logger LOG = 
        LoggerFactory.getLogger(TestExecution.class);

    /** 
     * Constant for the m_varStore of the last return value of the last
     * executed Action 
     */
    private static final String LAST_ACTION_RETURN = "TEST_LAR"; //$NON-NLS-1$
    
    /** 
     * Constant for the m_varStore of the last return value of the current
     * data set number
     */
    private static final String CURRENT_DATASET_NUMBER = "TEST_CDN"; //$NON-NLS-1$
    
    /** Singleton Instance of TestExecution */
    private static TestExecution instance = null;
    
    /** The timeout to use for cap-test-requests in MILLISECONDS! */
    private int m_requestTimeout = 
        TimeoutConstants.CLIENT_SERVER_TIMEOUT_DEFAULT_REQUEST;

    /** StepSpeed */
    private int m_stepSpeed = TimingConstantsClient.MIN_STEP_SPEED;

    /**
     * is execution paused
     */
    private boolean m_paused = false;
    
    /**
     * skip the next error
     */
    private boolean m_skipError = false;
    
    /**
     * is execution stopped ?
     */
    private boolean m_stopped = false;
    
    /**
     * indicates whether for test error events screenshots should be
     * automatically taken
     */
    private boolean m_autoScreenshot = true;

    /** the started Test Job */
    private ITestJobPO m_startedTestJob;
    
    /** the started TestSuite */
    private ITestSuitePO m_startedTestSuite;
    
    /** the CAP, that is actually executed */
    private ICapPO m_currentCap;

    /** <code>m_locale</code> locale (language) for testexecution */
    private Locale m_executionLanguage;

    /**
     * <code>m_trav</code> actual traverser for testexecution tree
     */
    private Traverser m_trav;
    
    /**
     * responsible for keeping track of the number of test steps executed
     * during this test execution 
     */
    private StepCounter m_stepCounter;
    
    /**
     * <code>m_resultTree</code> associated resultTree
     */
    private ResultTreeTracker m_resultTreeTracker;
    /**
     * The business process that performs component name operations.
     */
    private CompNamesBP m_compNamesBP = new CompNamesBP();
    
    /** business process for retrieving test data */
    private ExternalTestDataBP m_externalTestDataBP;
    
    /** Factory for IPostExecutionCommands */
    private PostExecCommandFactory m_postExecCmdFactory;
    
    /** The variable store */
    private TDVariableStore m_varStore;

    /** The variable store */
    private Map<String, Long> m_timerStore;
    
    /** 
     * the number of test steps that will be executed during this test execution
     * iff all of the following conditions are met:
     *     1. the test is not stopped prematurely
     *     2. the test does not fail prematurely (note that if the test fails
     *        on the final step, that step is still counted as "executed")
     *     3. no steps are executed while in an error state (no event handler 
     *        test steps are executed)
     */
    private int m_expectedNumberOfSteps;

    /**
     * Default constructor
     */
    private TestExecution() {
        m_varStore = new TDVariableStore();
        m_postExecCmdFactory = new PostExecCommandFactory();
        setTimerStore(new HashMap<String, Long>());
        m_externalTestDataBP = new ExternalTestDataBP();
        ClientTest.instance().addTestExecutionEventListener(
                new ITestExecutionEventListener() {
                    /**
                     * Clears this ExternalTestDataBP (e.g. the caches) 
                     * after TestExecution has finished.
                     */
                    public void endTestExecution() {
                        m_externalTestDataBP.clearExternalData();
                    }
                    /**
                     * {@inheritDoc}
                     */
                    public void stateChanged(TestExecutionEvent event) {
                        // nothing
                    }
                });
    }

    /**
     * Returns the singleton instance of TestExecution
     * 
     * @return singleton instance
     */
    public static synchronized TestExecution getInstance() {
        if (instance == null) {
            instance = new TestExecution();
        }
        return instance;
    }

    /**
     * This method executes the given Test Suite
     * 
     * @param testSuite
     *            the TestSuitePO that will be tested
     * @param locale
     *            Locale
     * @param autoScreenshot
     *            whether screenshots should be automatically taken in case of
     *            test execution errors
     * @param autId
     *            The ID of the Running AUT on which the test will take place.
     * @param externalVars
     *            a map of externally set variables; may be <code>null</code>
     * @param summary
     *            The Test Result Summary for the executed test. 
     *            Must not be <code>null</code>.
     * @param monitor the monitor to use
     * @param noRunOptMode 
     *            The value of no-run option argument if it was specified, null otherwise
     */
    public void executeTestSuite(ITestSuitePO testSuite, Locale locale,
        AutIdentifier autId, boolean autoScreenshot,
        Map<String, String> externalVars, ITestResultSummaryPO summary,
        final IProgressMonitor monitor, String noRunOptMode) {
        m_stopped = false;

        m_autoScreenshot = autoScreenshot;
        setPaused(false);
        Validate.notNull(testSuite, Messages.TestsuiteMustNotBeNull);
        m_executionLanguage = locale;
        m_externalTestDataBP.clearExternalData();
        if (TestExecution.shouldExecutionStop (noRunOptMode, 
              TestExecutionConstants.RunSteps.PTE)) {
            monitor.setCanceled(true);
            return;
        }
        try {
            if (AUTConnection.getInstance().connectToAut(
                    autId, new SubProgressMonitor(monitor, 0))) {
                if (TestExecution.shouldExecutionStop (noRunOptMode, 
                        TestExecutionConstants.RunSteps.CA)) {
                    endTestExecution();
                    return;
                }
                summary.setAutHostname(
                        AUTConnection.getInstance().getCommunicator()
                            .getConnection().getAddress()
                            .getCanonicalHostName());
                summary.setAutAgentName(AutAgentConnection.getInstance()
                        .getCommunicator().getHostName());
                monitor.subTask(Messages.
                        StartingTestSuite_resolvingPredefinedVariables);
                m_varStore.storeEnvironmentVariables();
                storePredefinedVariables(m_varStore, testSuite);
                storeExternallyDefinedVariables(m_varStore, externalVars);
                if (TestExecution.shouldExecutionStop (noRunOptMode,
                        TestExecutionConstants.RunSteps.RPV)) {
                    endTestExecution();
                    return;
                }
                startTestSuite(testSuite, locale, monitor, noRunOptMode);
                
                final AtomicBoolean testSuiteFinished = new AtomicBoolean();
                ClientTest.instance().addTestExecutionEventListener(
                        new ITestExecutionEventListener() {
                            public void endTestExecution() {
                                try {
                                    AUTConnection.getInstance().close();
                                } catch (ConnectionException e) {
                                    // Do nothing. Connection is already closed.
                                }
                                ClientTest.instance()
                                        .removeTestExecutionEventListener(this);
                                testSuiteFinished.set(true);
                            }

                            public void stateChanged(TestExecutionEvent event) {
                                // Do nothing
                            }
                        });
                while (!testSuiteFinished.get()) {
                    TimeUtil.delay(250);
                }
            } else {
                handleNoConnectionToAUT(testSuite, autId);
            }
        } catch (ConnectionException e) {
            LOG.error(Messages.UnableToConnectToAUT + StringConstants.DOT, e);
        }
    }
    
    /**
     * @param varStore
     *            the variable store
     * @param externalVars
     *            a map of variables to add to the var store; may be
     *            <code>null</code>
     */
    private void storeExternallyDefinedVariables(TDVariableStore varStore,
        Map<String, String> externalVars) {
        if (externalVars != null) {
            for (String key : externalVars.keySet()) {
                varStore.store(key, externalVars.get(key));
            }
        }
    }

    /**
     * @param testSuite
     *            the test suite
     * @param autId
     *            the aut id
     */
    private void handleNoConnectionToAUT(ITestSuitePO testSuite,
            AutIdentifier autId) {
        String autName = autId.getExecutableName();
        if (isAutNameSet(autName)) {
            // no AUTid for test suite has been set
            autName = NLS.bind(Messages.ErrorDetailNO_AUT_ID_FOR_REF_TS_FOUND,
                    testSuite.getName());
        }
        ClientTest.instance().fireTestExecutionChanged(
                new TestExecutionEvent(State.TEST_EXEC_FAILED,
                        new JBException(Messages.CouldNotConnectToAUT + autName,
                                MessageIDs.E_NO_AUT_CONNECTION_ERROR)));
    }
    
    /**
     * @param autName the AUT Id
     * @return whether the AUT name is correctly set
     */
    public static boolean isAutNameSet(String autName) {
        return String.valueOf(autName).equals("null"); //$NON-NLS-1$
    }

    /**
     * Initializes all pre-defined variables for execution of the given 
     * test suite.
     * 
     * @param varStore The place to store the variables.
     * @param testSuite The test suite that will be executed. Some variables
     *                  have values based on this test suite.
     */
    private void storePredefinedVariables(TDVariableStore varStore, 
            ITestSuitePO testSuite) {

        // TEST_language
        varStore.store(TDVariableStore.VAR_LANG, 
                m_executionLanguage.toString());

        // TEST_testsuite
        varStore.store(TDVariableStore.VAR_TS, testSuite.getName());

        // TEST_username
        varStore.store(TDVariableStore.VAR_USERNAME, 
                System.getProperty("user.name")); //$NON-NLS-1$
        
        // TEST_dbusername
        varStore.store(TDVariableStore.VAR_DBUSERNAME, 
                Persistor.instance().getCurrentDBUser());
        
        IProjectPO cProject = GeneralStorage.getInstance().getProject();
        
        // TEST_PROJECT_NAME
        varStore.store(TDVariableStore.VAR_PROJECT_NAME, 
                cProject.getName());
        
        // TEST_PROJECT_VERSION
        varStore.store(TDVariableStore.VAR_PROJECT_VERSION, 
                cProject.getVersionString());
        
        try {
            AutAgentConnection serverConn = AutAgentConnection.getInstance();

            // TEST_autstarter
            varStore.store(TDVariableStore.VAR_AUTAGENT, 
                    serverConn.getCommunicator().getHostName());
            
            // TEST_portnumber
            varStore.store(TDVariableStore.VAR_PORT, 
                    String.valueOf(serverConn.getCommunicator().getPort()));

        } catch (ConnectionException ce) {
            // No connection to AutStarter.
            // Do nothing.
        }
        
        // TEST_aut
        varStore.store(TDVariableStore.VAR_AUT, testSuite.getAut().getName());
        
        // TEST_autconfig
        Map<String, String> autConfigMap = 
            getConnectedAUTsConfigMap();
        if (autConfigMap != null) {
            varStore.store(TDVariableStore.VAR_AUTCONFIG, MapUtils.getString(
                    autConfigMap, AutConfigConstants.AUT_CONFIG_NAME, 
                    TestresultSummaryBP.AUTRUN));
        } else {
            // write constant for AUTs which has been started via autrun
            varStore.store(TDVariableStore.VAR_AUTCONFIG, 
                    TestresultSummaryBP.AUTRUN);
        }

        // TEST_clientVersion
        varStore.store(TDVariableStore.VAR_CLIENTVERSION, 
                Platform.getBundle(
                        Activator.PLUGIN_ID).getHeaders().get(
                                Constants.BUNDLE_VERSION));
        
    }

    /**
     * @return the aut config map of the currently connected aut or null if
     *         there is no currently connected aut
     */
    protected Map<String, String> getConnectedAUTsConfigMap() {
        if (TestExecution.getInstance().getConnectedAutId() != null) {
            String autID = getConnectedAutId().getExecutableName();
            return ClientTest.instance()
                .requestAutConfigMapFromAgent(autID);
        }
        return null;
    }
    
    
    /**
     * @param testSuite testSuite
     * @param locale language valid for testexecution
     * @param monitor the progress monitor to use
     * @param noRunOptMode the value of no-run option argument if it was specified, null otherwise
     */
    private void startTestSuite(ITestSuitePO testSuite, Locale locale,
        IProgressMonitor monitor, String noRunOptMode) {
        Validate.notNull(testSuite, "No testsuite available"); //$NON-NLS-1$
        ICapPO firstCap = null;
        m_expectedNumberOfSteps = 0;
        m_trav = new Traverser(testSuite, locale);
        try {
            // build and show result Tree
            monitor.subTask(Messages.
                    StartingTestSuite_resolvingTestStepsToExecute);
            monitor.subTask(Messages.
                    StartingTestSuite_buildingTestExecutionTree);
            Traverser copier = new Traverser(testSuite, locale);
            ResultTreeBuilder resultTreeBuilder = new ResultTreeBuilder(copier);
            copier.addExecStackModificationListener(resultTreeBuilder);
            ICapPO iterNode = copier.next();
            while (iterNode != null) {
                iterNode = copier.next();
                m_expectedNumberOfSteps++;
            }
            Map<String, String> autConfigMap = getConnectedAUTsConfigMap();
            resetMonitoringData(autConfigMap, monitor);
            if (TestExecution.shouldExecutionStop(noRunOptMode,
                    TestExecutionConstants.RunSteps.BT)) {
                endTestExecution();
                return;
            }
            // end build tree
            TestResultBP.getInstance().setResultTestModel(
                    new TestResult(resultTreeBuilder.getRootNode(),
                            autConfigMap));
            initTestExecutionMessage(autConfigMap, monitor);
            
            m_resultTreeTracker = new ResultTreeTracker(resultTreeBuilder.
                    getRootNode(), m_externalTestDataBP);
            IProgressMonitor subMonitor = new SubProgressMonitor(monitor,
                    ClientTestImpl.TEST_SUITE_EXECUTION_RELATIVE_WORK_AMOUNT);
            subMonitor.beginTask(
                    NLS.bind(Messages.StartWorkingWithTestSuite,
                            testSuite.getName()),
                    m_expectedNumberOfSteps);
            m_stepCounter = new StepCounter(subMonitor);
            addTestExecutionListener();

            // set global delay for each test step
            setStepSpeed(testSuite.getStepDelay());
            ClientTest.instance().
                fireTestExecutionChanged(new TestExecutionEvent(
                        State.TEST_EXEC_RESULT_TREE_READY));
            monitor.subTask(
                    NLS.bind(Messages.StartingTestSuite,
                    testSuite.getName()));
            firstCap = m_trav.next();
        } catch (JBException e) {
            LOG.error(Messages.IncompleteTestdata, e);
            fireError(e);
        }
        if (firstCap != null) {
            ClientTest.instance().
                fireTestExecutionChanged(new TestExecutionEvent(
                        State.TEST_EXEC_START));
            processCap(firstCap);
        } else {
            endTestExecution();
        }
    }

    /**
     * add the listener to the test execution traverser
     */
    private void addTestExecutionListener() {
        m_trav.addExecStackModificationListener(m_resultTreeTracker);
        m_trav.addEventStackModificationListener(m_stepCounter);
        m_trav.addExecStackModificationListener(m_stepCounter);
    }

    /**
     * @return the toolkit of the AUT
     */
    private String getAutToolkit() {
        String autToolKit = StringConstants.EMPTY;
        final IAUTMainPO connectedAut =  getConnectedAut();
        if (connectedAut != null) {
            autToolKit = connectedAut.getToolkit();            
        }
        return autToolKit;
    }
    
    /**
     * Invokes the next step
     * 
     * @param cap cap, which to create the corresponding message for
     * 
     */
    private void processCap(ICapPO cap) {
        ICapPO currCap = cap;
        MessageCap messageCap = null;
        if (currCap == null) {
            endTestExecution();
            return;
        }
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug(Messages.TestStep + StringConstants.COLON
                        + StringConstants.SPACE + currCap.getName());
                LOG.debug(Messages.Component + StringConstants.COLON
                        + StringConstants.SPACE + currCap.getComponentName());
            }
            messageCap = buildMessageCap(currCap, false);
            if (!m_stopped) {
                CAPTestMessage capTestMessage = new CAPTestMessage(messageCap);
                // StepSpeed
                TimeUtil.delay(m_stepSpeed);
                while (isPaused()) {
                    testConnection();
                    TimeUtil.delay(100);
                }
                if (!m_stopped) {
                    CAPTestResponseMessage clientResponse = 
                        clientExecutionHandling(currCap, capTestMessage);

                    if (!m_stopped) { // project reload during AUT restart
                        // may trigger this
                        if (clientResponse != null) {
                            // The result of the CAP has already been determined
                            // by the
                            // client.
                            CAPTestResponseCommand responseCommand = 
                                new CAPTestResponseCommand();
                            responseCommand.setMessage(clientResponse);
                            responseCommand.setMessageCap(messageCap);
                            responseCommand.execute();
                        } else {
                            final int timeOut = 
                                calculateRequestTimeout(messageCap);
                            // send message to server
                            AUTConnection.getInstance().request(capTestMessage,
                                    new CAPTestResponseCommand(), timeOut);
                        }
                    }
                } else {
                    endTestExecution();
                }
            }
        } catch (NotConnectedException bnce) {
            LOG.error(Messages.AUTConnectionFails, bnce);
        } catch (CommunicationException bce) {
            LOG.error(Messages.CommunicationWithAUTFails, bce);
            fireError(bce);
        } catch (LogicComponentNotManagedException blcnme) {
            LOG.error(blcnme.getMessage(), blcnme);
            fireComponentError();
        } catch (InvalidDataException ide) { // NOPMD by al on 3/19/07 1:24 PM
            // never happens here, because buildMessageCap(cap, false) is called
            // with false!
        }
    }
    
    /**
     * Calculates the request timeout. Important if an action has a higher
     * timeout than the standard request timeout.
     * @param messageCap the MessageCap
     * @return the calculated timeout.
     */
    private int calculateRequestTimeout(MessageCap messageCap) {
        List<Integer> timeOuts = new ArrayList<Integer>();
        IParamDescriptionPO desc1 = 
            m_currentCap.getParameterForUniqueId(COMP_SYSTEM_TIMEOUT);
        timeOuts.add(m_currentCap.getParameterList().indexOf(desc1));
        desc1 = m_currentCap.getParameterForUniqueId("CompSystem.TimeMillSec"); //$NON-NLS-1$
        timeOuts.add(m_currentCap.getParameterList().indexOf(desc1));
        
        int timeout = 0;
        for (int index : timeOuts) {
            if (index > -1) {
                final MessageParam param = messageCap
                    .getMessageParams().get(index);
                final String paramTimeOut = param.getValue();
                timeout += Integer.parseInt(paramTimeOut);
            }
        }
        
        // Special handling for Show Text
        boolean isShowText = messageCap.getMethod().equals("rcShowText"); //$NON-NLS-1$
        if (isShowText) {
            int showTextTimeout = calculateShowTextTimeout(messageCap);
            if (showTextTimeout != -1) {
                timeout += showTextTimeout;
            }
        }

        return m_requestTimeout + timeout;
    }
    
    /**
     * Handles the client-actions
     * @param cap the cap to execute
     * @param capTestMessage the CAPTestMessage.
     * @return a response if the result of testing the CAP is determined 
     *         entirely by the client. Returns <code>null</code> if the CAP
     *         message should be sent to the server.
     */
    private CAPTestResponseMessage clientExecutionHandling(ICapPO cap, 
        CAPTestMessage capTestMessage) {
        
        Action action = cap.getMetaAction();
        if (!action.isClientAction()) {
            return null;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(Messages.ExecutingClientAction + StringConstants.COLON
                + StringConstants.SPACE + action.getPostExecutionCommand());
        }
        final String postExecCommandClass = action.getPostExecutionCommand();
        final IPostExecutionCommand command = m_postExecCmdFactory
            .createCommand(postExecCommandClass);
        TestErrorEvent errorEvent = executePostExecCommand(command);
        
        if (errorEvent != null) { 
            CAPTestResponseMessage response = new CAPTestResponseMessage();
            response.setTestErrorEvent(errorEvent);
            response.setMessageCap(capTestMessage.getMessageCap());
            return response;
        }
        
        return null;
    }
    
    /**
     * Calculates the timeout for a Show Text Action
     * 
     * @param messageCap The message cap
     * @return The timeout to use, or -1 if the timeout cannot be calculated
     */
    private int calculateShowTextTimeout(MessageCap messageCap) {
        MessageParam textParam = 
            messageCap.getMessageParams().get(0);
        MessageParam timePerWordParam = 
            messageCap.getMessageParams().get(2);
        try {
            int numWords = StringParsing.countWords(textParam.getValue());
            return Integer.parseInt(timePerWordParam.getValue()) 
                * numWords;
        } catch (NumberFormatException e) {
            LOG.warn(Messages.ErrorParsingTimeoutParameter + StringConstants.DOT
                    + StringConstants.SPACE + Messages.UsingDefaultValue 
                    + StringConstants.DOT, e);
        }
        return -1;
    }

    /**
     * Builds the messageCap with the data sending to server
     * 
     * @param cap cap to create the corresponding message cap for
     * @param runIncomplete sets this method in "run incomplete"-mode.
     *  It throws InvalidDataException if missing test data.
     * @return MessageCap
     * @throws LogicComponentNotManagedException
     *             if component not found in objectMap.
     *             if building compSystem fails.
     *             if component cannot be found.
     * @throws InvalidDataException in case of "run incomplete"-mode
     * and missing test data.
     */
    private MessageCap buildMessageCap(ICapPO cap, boolean runIncomplete)
        throws InvalidDataException, LogicComponentNotManagedException {
        
        MessageCap messageCap;
        String logicalName = null;
        try {
            messageCap = new MessageCap();
            CompSystem compSystem = ComponentBuilder.getInstance()
                .getCompSystem();
            m_currentCap = cap;
            ITestSuitePO ts = (ITestSuitePO)m_trav.getRoot();
            IAUTMainPO aut = ts.getAut();
            Component comp = compSystem.findComponent(cap.getComponentType());
            // Find the component name. It may be overridden in one or
            // more ExecTestCase nodes.
            if (!StringUtils.isEmpty(cap.getComponentName())) {
                logicalName = m_compNamesBP.findCompName(
                        m_trav.getExecStackAsNodeList(), cap, 
                        cap.getComponentName(),
                        ComponentNamesBP.getInstance()).getCompName();
            }
            messageCap.setResolvedLogicalName(logicalName);
            IComponentIdentifier technicalName = null;
            
            technicalName = getTechnicalName(logicalName, aut, comp);
            if (comp.isConcrete() && ((ConcreteComponent)comp)
                    .hasDefaultMapping()) {
                messageCap.sethasDefaultMapping(true);
            }
            if (technicalName == null) {
                throw new LogicComponentNotManagedException(
                        StringConstants.EMPTY,
                        MessageIDs.E_COMPONENT_NOT_MANAGED);
            }
            Action action = comp.findAction(cap.getActionName());
            messageCap.setAction(action);
            messageCap.setMethod(action.getMethod());
            messageCap.setPostExecutionCommand(
                action.getPostExecutionCommand());
            messageCap.setCi(technicalName);
            if (cap.getParameterList() != null) {
                messageCap = configureMessageCap(cap, messageCap, action, 
                    runIncomplete);
            }
            return messageCap;
        } catch (LogicComponentNotManagedException blcnme) {
            StringBuilder msg = new StringBuilder();
            msg.append(Messages.NoEntryFor);
            msg.append(StringConstants.SPACE);
            msg.append(cap.getComponentName());
            msg.append(StringConstants.LEFT_PARENTHESES);
            msg.append(StringConstants.EQUALS_SIGN);
            msg.append(Messages.ProfessionalName);
            msg.append(StringConstants.RIGHT_PARENTHESES);
            msg.append(StringConstants.SPACE);
            msg.append(StringConstants.SLASH);
            msg.append(StringConstants.SPACE);
            msg.append(StringConstants.LEFT_PARENTHESES);
            msg.append(StringConstants.EQUALS_SIGN);
            msg.append(Messages.TechnicalName);
            msg.append(StringConstants.RIGHT_PARENTHESES);
            LOG.error(msg.toString(), blcnme);
            throw blcnme;
        } catch (InvalidDataException e) {
            if (runIncomplete) {
                throw e;
            }
            // Never happens if runIncomplete==false because called method
            // handles this exception in this case!
            return null;
        }
    }

    /**
     * 
     * @param logicalName
     *            guid of the logical name for which to find the technical name
     * @param aut
     *            AUT information
     * @param comp
     *            component
     * @return a ComponentIdentifier or null
     */
    private IComponentIdentifier getTechnicalName(String logicalName,
            IAUTMainPO aut,  Component comp) {
        IObjectMappingPO om = aut.getObjMap(); 
        IComponentIdentifier technicalName;
        try {
            technicalName = om.getTechnicalName(logicalName);
        } catch (LogicComponentNotManagedException e) {
            technicalName = null;
        }
        if (technicalName == null && comp instanceof ConcreteComponent) {
            ConcreteComponent cc = ((ConcreteComponent) comp);
            String toolkit = aut.getToolkit();
            if (cc.hasDefaultMapping() && cc.getComponentClass() != null) {
                return ToolkitSupportBP.
                    getIdentifierOfMostAbstractRealizingComponentInToolkit(
                        toolkit, cc);
            }
        }
        return technicalName;
    }
    
    /**
     * sets the properties of messageCap
     * 
     * @param cap cap
     * @param messageCap corresponding messageCap
     * @param action corresponding action
     * @param runIncomplete if true, throws an InvalidDataException
     * if missing test data, otherwise it handles the exception itself.
     * @throws InvalidDataException in case of missing testdata in 
     * "run incomplete"-mode.
     * @return configured messageCap
     */
    private MessageCap configureMessageCap(ICapPO cap, MessageCap messageCap,
        Action action, boolean runIncomplete) throws InvalidDataException {
        ITDManager tdManager = null;
        try {
            tdManager = m_externalTestDataBP.getExternalCheckedTDManager(cap);
        } catch (JBException gde) {
            fireError(gde);
        }
        if (tdManager != null) {
            for (IParamDescriptionPO desc : cap.getParameterList()) {
                if (m_stopped) {
                    // Stop processing parameters if execution has already 
                    // stopped.
                    return messageCap;
                }
                MessageParam messageParam = createMessageParam(desc, action);
                messageCap.addMessageParam(messageParam);
                ITestDataPO date = 
                    tdManager.getCell(0, desc);
                StringBuilder msg = new StringBuilder();
                msg.append(Messages.NoTestdataAvailableForCAP);
                msg.append(StringConstants.COLON);
                msg.append(StringConstants.SPACE);
                msg.append(cap.getName());
                msg.append(StringConstants.COMMA);
                msg.append(StringConstants.SPACE);
                msg.append(Messages.Parameter);
                msg.append(StringConstants.COLON);
                msg.append(StringConstants.SPACE);
                msg.append(desc.getName());
                msg.append(StringConstants.COMMA);
                msg.append(StringConstants.SPACE);
                msg.append(Messages.AndDatasetNumberZero);
                Validate.notNull(date, msg.toString());
                String value = null;
                try {
                    final int dsNumber = m_trav.getDataSetNumber();
                    m_varStore.store(CURRENT_DATASET_NUMBER, String.valueOf(
                        dsNumber + 1)); // 1-based for the user!
                    ParamValueConverter conv = new ModelParamValueConverter(
                        date.getValue(getLocale()), cap,
                        getLocale(), desc);
                    List <ExecObject> stackList = 
                        new ArrayList<ExecObject>(m_trav.getExecStackAsList());
                    value = conv.getExecutionString(stackList, getLocale());
                } catch (InvalidDataException e) {
                    if (!runIncomplete) {
                        StringBuilder msgbuild = new StringBuilder();
                        msgbuild.append(Messages.NoValueAvailableForParameter);
                        msgbuild.append(StringConstants.COLON);
                        msgbuild.append(StringConstants.SPACE);
                        msgbuild.append(desc.getName());
                        msgbuild.append(StringConstants.SPACE);
                        msgbuild.append(Messages.InNode);
                        msgbuild.append(StringConstants.COLON);
                        msgbuild.append(StringConstants.SPACE);
                        msgbuild.append(cap.getName());
                        
                        LOG.error(msgbuild.toString(), e);
                        fireError(e);
                    } else {
                        throw e;
                    }
                }
                messageParam.setValue(value);
            }
        }
        return messageCap;
    }
    
    
    /**
     * creates a messagParam corresponding to CAPParamDescription
     * 
     * @param desc parameter object
     * @param action corresponding action to this cap
     * @return appropriate MessageParam
     */
    private MessageParam createMessageParam(IParamDescriptionPO desc,
        Action action) {

        Param xmlParam = action.findParam(desc.getUniqueId());
        if (LOG.isDebugEnabled()) {
            LOG.debug(Messages.Param + StringConstants.COLON 
                    + StringConstants.SPACE + xmlParam.getName());            
        }
        MessageParam messageParam = new MessageParam();
        messageParam.setType(xmlParam.getType());
        return messageParam;
    }


    /**
     * verifies the response of server for execution of a cap
     * @param msg The response message.
     */
    public void processServerResponse(final CAPTestResponseMessage msg) {
        Thread t = new IsAliveThread("Execute Test Step") { //$NON-NLS-1$
            public void run() {
                ICapPO nextCap = null;
                processPostExecution(msg);
                TestResultNode resultNode = m_resultTreeTracker.getEndNode();
                MessageCap mc = msg.getMessageCap();
                resultNode.setComponentName(
                    ComponentNamesBP.getInstance().getName(
                            mc.getResolvedLogicalName(), 
                        GeneralStorage.getInstance().getProject().getId()));
                IComponentIdentifier ci = mc.getCi();
                resultNode.setOmHeuristicEquivalence(
                        ci.getMatchPercentage());
                resultNode.setNoOfSimilarComponents(
                        ci.getNumberOfOtherMatchingComponents());
                final boolean testOk = !msg.hasTestErrorEvent();
                if (msg.getState() == CAPTestResponseMessage.PAUSE_EXECUTION) {
                    pauseExecution(PauseMode.PAUSE);
                }
                if (testOk) {
                    resultNode.setResult(m_trav.getSuccessResult(), null);
                } else {
                    // ErrorEvent has occured
                    TestErrorEvent event = msg.getTestErrorEvent();
                    if (m_trav.getEventHandlerReentry(event.getId()).equals(
                            ReentryProperty.RETRY)) {
                        resultNode.setResult(TestResultNode.RETRYING, event);
                    } else {
                        m_stepCounter.incrementNumberOfFailedSteps();
                        resultNode.setResult(TestResultNode.ERROR, event);
                        if (m_autoScreenshot) {
                            addScreenshot(resultNode);
                        }
                        if (ClientTest.instance()
                                .isPauseTestExecutionOnError()) {
                            pauseExecution(PauseMode.PAUSE);
                        }
                    }
                }
                while (isPaused()) {
                    testConnection();
                    TimeUtil.delay(100);
                }
                
                if (!m_stopped) {
                    try {
                        nextCap = testOk || m_skipError ? m_trav.next()
                                : m_trav.next(msg.getTestErrorEvent().getId());
                        m_skipError = false;
                    } catch (JBException e) {
                        LOG.error(Messages.IncompleteTestdata, e);
                        fireError(e);
                    }
                    if (nextCap != null) {
                        processCap(nextCap);
                    } else {
                        if (LOG.isInfoEnabled()) {
                            LOG.info(Messages.TestsuiteFinished);
                        }
                        endTestExecution();
                    }
                }
            }
        };
        t.start();
    }

    /**
     * @param resultNode the result node to add the screenshot for
     */
    private void addScreenshot(TestResultNode resultNode) {
        // Send request to AUT and wait for response
        ICommand command = new TakeScreenshotResponseCommand(resultNode);
        Message message = new TakeScreenshotMessage();
        try {
            AUTConnection.getInstance().request(
                    message, command, 
                    TimeoutConstants.CLIENT_SERVER_TIMEOUT_TAKE_SCREENSHOT);
        } catch (NotConnectedException nce) {
            if (LOG.isErrorEnabled()) {
                LOG.error(nce.getLocalizedMessage(), nce);
            }
        } catch (CommunicationException ce) {
            if (LOG.isErrorEnabled()) {
                LOG.error(ce.getLocalizedMessage(), ce);
            }
        }
    }

    /**
     * Processes the post execution of an action
     * @param msg the CAPTestResponseMessage.
     */
    private void processPostExecution(CAPTestResponseMessage msg) {
        m_varStore.store(LAST_ACTION_RETURN, msg.getReturnValue());
        final String cmdClassName = msg.getMessageCap()
            .getPostExecutionCommand();
        if (cmdClassName != null && cmdClassName.length() > 0 
            && !m_currentCap.getMetaAction().isClientAction()) {

            TestErrorEvent errorEvent = executePostExecCommand(
                    m_postExecCmdFactory.createCommand(cmdClassName));
            if (msg.getTestErrorEvent() == null 
                && errorEvent != null) { 
                
                msg.setTestErrorEvent(errorEvent); 
            }
        }
    }
    
    /**
     * Loads, instantiates and executes the given IPostExecutionCommand class
     * 
     * @param cmd
     *            the IPostExecutionCommand to execute.
     * @return a TestErrorEvent representing an error that occurred during
     *         execution, or <code>null</code> if no such error occurs.
     */
    private TestErrorEvent executePostExecCommand(IPostExecutionCommand cmd) {
        if (cmd instanceof AbstractPostExecutionCommand) {
            AbstractPostExecutionCommand aCmd = 
                    (AbstractPostExecutionCommand) cmd;
            aCmd.setCurrentCap(m_currentCap);
            aCmd.setExternalTestDataBP(m_externalTestDataBP);
            aCmd.setLocale(getLocale());
            aCmd.setTraverser(m_trav);
        }
        try {
            return cmd.execute();
        } catch (JBException e) {
            LOG.error(NLS.bind(Messages.ErrorExecutingCommand, cmd.getClass()
                    .getName(), e.getLocalizedMessage()));
            fireError(e);
            return null;
        }
    }
    

    /**
     * Tests the connection to server (sends a NullMessage to server)
     * 
     */
    protected void testConnection() {
        try {
            AUTConnection.getInstance().send(new NullMessage());
        } catch (CommunicationException e) {
            fireError(new JBException(MessageIDs.getMessage(
                    MessageIDs.E_INTERRUPTED_CONNECTION), 
                    MessageIDs.E_INTERRUPTED_CONNECTION));
        }
    }

    /**
     * Sends a init test execution message
     * 
     * @param autConfigMap
     *            the config map to use
     * @param monitor the monitor to use
     */
    private void initTestExecutionMessage(Map<String, String> autConfigMap, 
        IProgressMonitor monitor) {
        try {
            InitTestExecutionMessage msg = new InitTestExecutionMessage();
            if (autConfigMap != null) {
                monitor.subTask(Messages.
                        StartingTestSuite_activatingAUT);
                msg.setDefaultActivationMethod(ActivationMethod
                        .getRCString(autConfigMap
                                .get(AutConfigConstants.ACTIVATION_METHOD)));
                AUTConnection.getInstance().send(msg);
            }
        } catch (CommunicationException exc) {
            fireError(exc);
        }
    }

    /**
     * Fires an event if test fails
     * 
     * @param e
     *            Exception
     */
    private void fireError(Exception e) {
        ClientTest.instance().fireTestExecutionChanged(
                new TestExecutionEvent(State.TEST_EXEC_FAILED, e));
        endTestExecution();
    }

    /**
     * Fires an event if test fails, because the component name is wrong.
     */
    private void fireComponentError() {
        ClientTest.instance().
            fireTestExecutionChanged(new TestExecutionEvent(
                State.TEST_EXEC_COMPONENT_FAILED));
        endTestExecution();
    }
    
    /**
     * 
     * @param stepSpeed
     *            The stepSpeed to set.
     */
    public void setStepSpeed(int stepSpeed) {
        if (stepSpeed > TimingConstantsClient.MIN_STEP_SPEED) {
            m_stepSpeed = stepSpeed;
        } else {
            m_stepSpeed = TimingConstantsClient.MIN_STEP_SPEED;
        }
    }

    /**
     * Stop the test execution
     *  
     */
    public void stopExecution() {
        synchronized (this) {
            if (!m_stopped) {
                m_stopped = true;
                setPaused(false);
                
                m_timerStore.clear();
                
                try {
                    AUTConnection.getInstance().getCommunicator()
                    .interruptAllTimeouts();
                } catch (ConnectionException e) {
                    fireError(e);
                }
                if (LOG.isInfoEnabled()) {
                    LOG.info(Messages.TestsuiteIsStopped);
                }
                
                ClientTest.instance().fireEndTestExecution();
                try {
                    AUTConnection.getInstance().close();
                } catch (ConnectionException e) {
                    // Do nothing. Connection already closed.
                }
            }
        }
    }

    /**
     * This method will reset the profiling agent.
     * 
     * @param autConfigMap
     *            the aut config map to use
     * @param monitor the monitor to use
     */
    private void resetMonitoringData(Map<String, String> autConfigMap, 
        IProgressMonitor monitor) {
        if (autConfigMap != null) {
            String resetString = autConfigMap
                    .get(MonitoringConstants.RESET_AGENT);
            if (resetString != null) {
                boolean reset = Boolean.valueOf(resetString);
                if (reset) {
                    try {
                        monitor.subTask(Messages.
                                StartingTestSuite_resettingMonitoringData);
                        ResetMonitoringDataMessage message = 
                            new ResetMonitoringDataMessage(
                                AUTConnection.getInstance().getConnectedAutId()
                                        .getExecutableName());
                        AutAgentConnection.getInstance().send(message);
                    } catch (NotConnectedException nce) {
                        LOG.error(nce.getLocalizedMessage(), nce);
                    } catch (CommunicationException ce) {
                        LOG.error(ce.getLocalizedMessage(), ce);
                    }
                }
            }
        }
    }

    /**
     * end the test execution normally
     */
    public void endTestExecution() {
        // Send request to aut starter and wait for response
        ICommand command = new EndTestExecutionResponseCommand();
        Message message = new EndTestExecutionMessage();
        try {
            AUTConnection.getInstance().request(message, command,
                    EndTestExecutionMessage.TIMEOUT);
        } catch (NotConnectedException nce) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(nce.getLocalizedMessage(), nce);
            }
            stopExecution();
        } catch (CommunicationException ce) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(ce.getLocalizedMessage(), ce);
            }
            stopExecution();
        }
    }

    /**
     * Toggles the pause state of the test execution
     * @param pm the pause mode to use
     */
    public void pauseExecution(PauseMode pm) {
        switch (pm) {
            case PAUSE:
                if (!isPaused()) {
                    pauseExecution(PauseMode.TOGGLE);
                }
                break;
            case UNPAUSE:
                if (isPaused()) {
                    pauseExecution(PauseMode.TOGGLE);
                }
                break;
            case TOGGLE:
                setPaused(!isPaused());
                if (isPaused()) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info(Messages.TestsuiteIsPaused);
                    }
                    ClientTest.instance().fireTestExecutionChanged(
                            new TestExecutionEvent(
                                    State.TEST_EXEC_PAUSED));
                } else {
                    if (LOG.isInfoEnabled()) {
                        LOG.info(Messages.TestexecutionHasResumed);
                    }
                    ClientTest.instance().fireTestExecutionChanged(
                            new TestExecutionEvent(
                                    State.TEST_EXEC_START));
                }
                break;
            case CONTINUE_WITHOUT_EH:
                m_skipError = true;
                pauseExecution(PauseMode.UNPAUSE);
                break;
            default:
                break;
        }
    }
    
    /**
     * timeout()
     */
    public void timeout() {
        m_resultTreeTracker.getEndNode().setResult(TestResultNode.ABORT, null);
        fireError(new JBException(MessageIDs.getMessage(MessageIDs.
                E_TIMEOUT_CONNECTION), MessageIDs.E_TIMEOUT_CONNECTION));
    }

    /**
     * @return Returns the actualCap.
     */
    public ICapPO getActualCap() {
        return m_currentCap;
    }
    
    /**
     * 
     * @return Locale of Execution
     */
    public Locale getLocale() {
        return m_executionLanguage;
    }

    /**
     * Traverser for Execution
     * @return Traverser
     */
    public Traverser getTrav() {
        return m_trav;
    }

    /**
     * @return true if Test Suite is paused
     */
    protected boolean isPaused() {
        return m_paused;
    }

    
    /**
     * @return the ID of the currently connected Running AUT.
     */
    public AutIdentifier getConnectedAutId() {
        try {
            return AUTConnection.getInstance().getConnectedAutId();
        } catch (ConnectionException e) {
            // Do nothing. No connection exists, so we'll just end up returning
            // null.
        }

        return null;
    }

    /**
     * 
     * @return the AUT Definition for the currently connected Running AUT.
     */
    public IAUTMainPO getConnectedAut() {
        return AutAgentRegistration.getAutForId(getConnectedAutId(), 
                GeneralStorage.getInstance().getProject());
    }
    
    /**
     * @return Returns the startedTestSuite.
     */
    public ITestSuitePO getStartedTestSuite() {
        return m_startedTestSuite;
    }

    /**
     * 
     * @return the started Test Job, or <code>null</code> if no Test Job is 
     *         currently running.
     */
    public ITestJobPO getStartedTestJob() {
        return m_startedTestJob;
    }

    /**
     * 
     * @param startedTestJob The Test Job to set.
     */
    public void setStartedTestJob(ITestJobPO startedTestJob) {
        m_startedTestJob = startedTestJob;
    }
    
    /**
     * @param startedTestSuite The startedTestSuite to set.
     */
    public void setStartedTestSuite(ITestSuitePO startedTestSuite) {
        m_startedTestSuite = startedTestSuite;
    }

    /**
     * @author BREDEX GmbH
     * @created 28.07.2006
     */
    private class PostExecCommandFactory {
        
        /**
         *  Cache of instantiated IPostExecutionCommands 
         *  The key is the full qualified name of the 
         *  IPostExecutionCommand class, the value is the instance of the
         *  class.
         */
        private Map<String, IPostExecutionCommand> m_commandCache = 
            new HashMap<String, IPostExecutionCommand>();
        
        
        /**
         * Instantiates an IPostExecutionCommand of the given class name.
         * @param commandClassName IPostExecutionCommand to instantiate
         * @return an IPostExecutionCommand instance
         */
        public IPostExecutionCommand createCommand(String commandClassName) {
            Class cmdClazz = null;
            Object cmdInstance = m_commandCache.get(commandClassName);
            if (cmdInstance != null) {
                return (IPostExecutionCommand)cmdInstance;
            }
            try {
                cmdClazz = Class.forName(commandClassName);
            } catch (ClassNotFoundException e) {
                LOG.error("ClassNotFoundException", e); //$NON-NLS-1$
                fireError(e);
            }
            Constructor constructor = null;
            try {
                constructor = cmdClazz.getConstructor(new Class[0]);
                cmdInstance = constructor.newInstance(new Object[0]);
            } catch (SecurityException e) {
                LOG.error("SecurityException", e); //$NON-NLS-1$
                fireError(e);
            } catch (NoSuchMethodException e) {
                try {
                    // maybe cmdClazz is a non static inner class 
                    // of TestExecution?
                    constructor = cmdClazz.getConstructor(
                        new Class[]{TestExecution.this.getClass()});
                    cmdInstance = constructor.newInstance(
                        new Object[]{TestExecution.this});
                } catch (SecurityException e1) {
                    LOG.error("SecurityException", e1); //$NON-NLS-1$
                    fireError(e1);
                } catch (NoSuchMethodException e1) {
                    LOG.error("NoSuchMethodException", e1); //$NON-NLS-1$
                    fireError(e1);
                } catch (IllegalArgumentException e1) {
                    LOG.error("IllegalArgumentException", e1); //$NON-NLS-1$
                    fireError(e1);
                } catch (InstantiationException e1) {
                    LOG.error("InstantiationException", e1); //$NON-NLS-1$
                    fireError(e1);
                } catch (IllegalAccessException e1) {
                    LOG.error("IllegalAccessException", e1); //$NON-NLS-1$
                    fireError(e1);
                } catch (InvocationTargetException e1) {
                    LOG.error("InvocationTargetException", e1); //$NON-NLS-1$
                    fireError(e1);
                }
            } catch (IllegalArgumentException e) {
                LOG.error("IllegalArgumentException", e); //$NON-NLS-1$
                fireError(e);
            } catch (InstantiationException e) {
                LOG.error("InstantiationException", e); //$NON-NLS-1$
                fireError(e);
            } catch (IllegalAccessException e) {
                LOG.error("IllegalAccessException", e); //$NON-NLS-1$
                fireError(e);
            } catch (InvocationTargetException e) {
                LOG.error("InvocationTargetException", e); //$NON-NLS-1$
                fireError(e);
            }
            IPostExecutionCommand cmd = (IPostExecutionCommand)cmdInstance;
            m_commandCache.put(commandClassName, cmd);
            return cmd;
            
        }
        
    }
    
    
    /**
     * IPostExecutionCommand to store a value read 
     * by Action "CompSystem.ReadValue" in the m_varStore
     * 
     * @author BREDEX GmbH
     * @created 24.07.2006
     */
    public class VariableStorerCmd extends AbstractPostExecutionCommand {
        /**
         * Constructor
         */
        public VariableStorerCmd() {
            super();
        }
        
        /**
         * {@inheritDoc}
         */
        public TestErrorEvent execute() throws JBException {
            // FIXME zeb Simply retrieving the first parameter has worked so 
            //           far because all Store/Read actions list Variable Name 
            //           as the first parameter. The first action that does not 
            //           follow this "convention" will result in errors 
            //           (possibly very subtle errors). We need to figure out a 
            //           way to make this generic (ex. as an argument to the 
            //           VariableStorerCmd defined in the 
            //           ComponentConfiguration.xml) in order to prevent 
            //           customers wishing to write extensions (as well as 
            //           ourselves!) from running head-first into this problem.
            IParamDescriptionPO desc = 
                m_currentCap.getParameterList().get(0); 
            try {
                ITDManager tdManager = 
                    m_externalTestDataBP.getExternalCheckedTDManager(
                            m_currentCap);
                ITestDataPO date = 
                    tdManager.getCell(0, desc);
                String varName = getValueForParam(date, m_currentCap, desc);
                m_varStore.store(varName, m_varStore.getValue(
                    LAST_ACTION_RETURN));
                return null;
            } catch (IllegalArgumentException e) {
                throw new JBException("IllegalArgumentException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new JBException("InvalidDataException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } 
        }
    }
    
    /**
     * abstract class for timer commands
     * 
     * @author BREDEX GmbH
     * @created 19.08.2009
     */
    public abstract class AbstractTimerCmd 
        extends AbstractPostExecutionCommand {

        /** @return the timer name */
        protected String getTimerName() throws JBException {
            return getValueForParam("CompSystem.TimerName"); //$NON-NLS-1$
        }
    }
    
    /**
     * IPostExecutionCommand to start a timer
     * by Action "CompSystem.StartTimer"
     *
     * @author BREDEX GmbH
     * @created 19.08.2009
     */
    public class StartTimerCmd extends AbstractTimerCmd {
        /**
         * {@inheritDoc}
         */
        public TestErrorEvent execute() throws JBException {
            try {
                String timerName = getTimerName();
                String variableName = getValueForParam(
                        "CompSystem.VariableToStoreAbsoluteStartTime"); //$NON-NLS-1$
                
                Long curTimeInMillisecs = 
                    new Long(System.currentTimeMillis());
                
                getTimerStore().put(timerName, curTimeInMillisecs);
                m_varStore.store(variableName, curTimeInMillisecs.toString());
            } catch (IllegalArgumentException e) {
                throw new JBException("IllegalArgumentException", e, //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new JBException("InvalidDataException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } 
            return null;
        }
    }
    
    /**
     * IPostExecutionCommand to read a timer
     * by Action "CompSystem.ReadTimer"
     *
     * @author BREDEX GmbH
     * @created 19.08.2009
     */
    public class ReadTimerCmd extends AbstractTimerCmd {
        /**
         * {@inheritDoc}
         */
        public TestErrorEvent execute() throws JBException {
            try {
                String timerName = getTimerName();
                String variableName = getValueForParam(
                        "CompSystem.VariableToStoreTimeDeltaSinceTimerStart"); //$NON-NLS-1$
                Long timerTimeInMillisecs = getTimerStore().get(timerName);
                if (timerTimeInMillisecs == null) {
                    return EventFactory.createActionError(
                            TestErrorEvent.TIMER_NOT_FOUND);
                }
                Long curTimeInMillisecs = new Long(System.currentTimeMillis());
                Long timeDelta = curTimeInMillisecs - timerTimeInMillisecs;
                m_varStore.store(variableName, timeDelta.toString());
            } catch (IllegalArgumentException e) {
                throw new JBException("IllegalArgumentException", e, //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new JBException("InvalidDataException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } 
            return null;
        }
    }

    /**
     * Implementation for the manual test step
     *
     * @author BREDEX GmbH
     * @created Aug 19, 2010
     */
    public class ManualTestStepCmd extends AbstractPostExecutionCommand {
        /**
         * <code>m_comment</code>
         */
        private String m_comment = null;
        
        /**
         * <code>m_status</code>
         */
        private boolean m_status = false;
        
        /**
         * {@inheritDoc}
         */
        public TestErrorEvent execute() throws JBException {
            try {
                String actionToPerform = 
                    getValueForParam("CompSystem.ActionToPerfom"); //$NON-NLS-1$
                String expectedBehavior = 
                    getValueForParam("CompSystem.ExpectedBehavior"); //$NON-NLS-1$
                int timeout = Integer.parseInt(
                    getValueForParam(COMP_SYSTEM_TIMEOUT));
                
                Message message = new DisplayManualTestStepMessage(
                        actionToPerform, expectedBehavior, timeout);
                ICommand command = 
                    new DisplayManualTestStepResponseCommand(this);

                AutAgentConnection.getInstance()
                    .request(message, command, timeout);
                
                int waited = 0;
                while ((command.getMessage() == null) && (waited <= timeout)) {
                    try {
                        Thread.sleep(200);
                        waited += 200;
                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                }

                if (!(waited <= timeout)) {
                    return EventFactory.createActionError(
                            TestErrorEvent.CONFIRMATION_TIMEOUT);
                } else if (!m_status) {
                    return EventFactory.createVerifyFailed(String
                            .valueOf(expectedBehavior), String
                            .valueOf(m_comment));
                }
                return null;
            } catch (IllegalArgumentException e) {
                throw new JBException("IllegalArgumentException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new JBException("InvalidDataException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } 
        }

        /**
         * @param comment the comment to set
         */
        public void setComment(String comment) {
            m_comment = comment;
        }

        /**
         * @return the comment
         */
        public String getComment() {
            return m_comment;
        }

        /**
         * @param status the status to set
         */
        public void setStatus(boolean status) {
            m_status = status;
        }

        /**
         * @return the status
         */
        public boolean isStatus() {
            return m_status;
        }
    }
    
    /**
     * @author BREDEX GmbH
     * @created 30.04.2013
     */
    private static class AUTTerminationListener 
        implements IAutRegistrationListener {
        /**
         * indicates whether the AUT terminated or not
         */
        private boolean m_autTerminated = false;
        /**
         * the AUTs ID to monitor
         */
        private AutIdentifier m_autId = null;
        /**
         * flag indicating that the AUT has been re-started
         */
        private AtomicBoolean m_hasAutRestarted = new AtomicBoolean(false);

        /**
         * @param autID
         *            the AUTs ID to monitor
         */
        public AUTTerminationListener(AutIdentifier autID) {
            m_autId = autID;
        }
        
        /** {@inheritDoc} */
        public void handleAutRegistration(
                AutRegistrationEvent event) {
            if (m_autId.equals(event.getAutId())) {
                if (event.getStatus() 
                        == RegistrationStatus.Deregister) {
                    setAutTerminated(true);
                }
                if (hasAutTerminated() && event.getStatus() 
                        == RegistrationStatus.Register) {
                    hasAutRestarted().set(true);
                }
            }
        }

        /**
         * @return the isAutRestarted
         */
        public AtomicBoolean hasAutRestarted() {
            return m_hasAutRestarted;
        }

        /**
         * @return the autTerminated
         */
        public boolean hasAutTerminated() {
            return m_autTerminated;
        }

        /**
         * @param autTerminated the autTerminated to set
         */
        private void setAutTerminated(boolean autTerminated) {
            m_autTerminated = autTerminated;
        }
    }
    
    /**
     * @author BREDEX GmbH
     * @created 30.04.2013
     */
    public class PrepareForShutdownCmd implements IPostExecutionCommand {
        /** {@inheritDoc} */
        public TestErrorEvent execute() throws JBException {
            AUTConnection.getInstance().getCommunicator()
                    .send(new PrepareForShutdownMessage(false, m_stepSpeed));
            return null;
        }
    }
    
    /**
     * @author BREDEX GmbH
     * @created 30.04.2013
     */
    public class SyncShutdownAndRestartCmd extends AbstractRestartCmd {
        @Override
        protected int getTerminationTimeout() throws JBException {
            String timeout = getValueForParam(COMP_SYSTEM_TIMEOUT);
            int parseInt = 0;
            try {
                parseInt = Integer.parseInt(timeout);
            } catch (NumberFormatException nfe) {
                LOG.error(nfe.getLocalizedMessage(), nfe);
            }
            return parseInt;
        }
    }
    
    /**
     * @author BREDEX GmbH
     * @created 30.04.2013
     */
    public abstract class AbstractRestartCmd 
        extends AbstractPostExecutionCommand {
        /**
         * timeout constant for using not timeout and force the AUTs restart
         */
        protected static final int NO_TIMEOUT__FORCE_RESTART = 0;
        
        /**
         * {@inheritDoc}
         */
        public final TestErrorEvent execute() throws JBException {
            final AutIdentifier autId = getConnectedAutId();
            AUTTerminationListener registrationListener = 
                    new AUTTerminationListener(autId);
            try {
                TimeUtil.delay(2000);
                
                if (LOG.isDebugEnabled()) {
                    LOG.debug(Messages.RequestingAUTAgentToCloseAUTConnection);
                }
                AUTConnection.getInstance().getCommunicator().
                    getConnectionManager().remove(
                        AUTConnection.getInstance()
                            .getCommunicator().getConnection());
                AUTConnection.getInstance().reset();
                AUTConnection.getInstance().close();
                boolean wasInterrupted = Thread.interrupted();
                AutAgentRegistration.getInstance().addListener(
                        registrationListener);
                IClientTest clientTest = ClientTest.instance();
                clientTest.fireAUTStateChanged(new AUTEvent(
                        AUTEvent.AUT_ABOUT_TO_TERMINATE));
                
                final int initialTerminationTimeout = getTerminationTimeout();
                final int terminationTimeout = initialTerminationTimeout
                        + TimeoutConstants.AUT_KEEP_ALIVE_DELAY_DEFAULT;
                final long startTime = System.currentTimeMillis();
                long endTime = 0;
                AutAgentConnection.getInstance().send(
                        new RestartAutMessage(autId, terminationTimeout));
                while (!registrationListener.hasAutRestarted().get()) {
                    // wait for AUT registration
                    try {
                        if (endTime == 0 && registrationListener
                                .hasAutTerminated()) {
                            endTime = System.currentTimeMillis();
                        }
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        // nothing
                        wasInterrupted = true;
                    }
                }
                // reconnect
                AUTConnection.getInstance().connectToAut(
                        autId, new NullProgressMonitor());
                if (wasInterrupted) {
                    Thread.currentThread().interrupt();
                }
                initTestExecutionMessage(getConnectedAUTsConfigMap(),
                        new NullProgressMonitor());
                long terminationDuration = endTime - startTime;
                if (initialTerminationTimeout > NO_TIMEOUT__FORCE_RESTART
                        && terminationDuration > terminationTimeout) {
                    return EventFactory.createActionError(
                            TestErrorEvent.TIMEOUT_EXPIRED);
                }
                return null;
            } finally {
                AutAgentRegistration.getInstance().removeListener(
                        registrationListener);
                if (LOG.isDebugEnabled()) {
                    LOG.debug(Messages.ContinueTestExecution);
                }
                if (!m_stopped) { // the AUT/TS may be stopped by a project load
                    ClientTest.instance().fireTestExecutionChanged(
                            new TestExecutionEvent(
                                    State.TEST_EXEC_RESTART));
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(Messages.CantContinueTSIsStopped);
                    }
                }
            }
        }

        /**
         * @return the timeout used to restart the AUT
         * @throws JBException
         *             in case of data retrieval problems
         */
        protected abstract int getTerminationTimeout() throws JBException;
    }
    
    /**
     * @author BREDEX GmbH
     * @created Aug 22, 2006
     */
    public class RestartCmd extends AbstractRestartCmd {
        @Override
        protected int getTerminationTimeout() {
            return NO_TIMEOUT__FORCE_RESTART;
        }
    }
    
    /**
     * Class for keeping track of the number of steps executed during a test.
     *
     * @author BREDEX GmbH
     * @created Aug 6, 2008
     */
    private static class StepCounter 
            implements IExecStackModificationListener, 
                       IEventStackModificationListener {

        /** total number of steps executed */
        private int m_totalSteps = 0;

        /** number of steps executed from within an event handler */
        private int m_eventHandlerSteps = 0;
        
        /** 
         * number of steps that fulfill the following criteria:
         *  a. marked as retried
         *  b. not executed from an event handler
         *   
         */
        private int m_retriedSteps = 0;
        
        /** number of failed test steps */
        private int m_failedSteps = 0;
        
        /** 
         * The current depth of the event handling stack. This is needed
         *  in order to determine whether the test is currently handling an
         *  "error" state.
         */
        private int m_currentEventStackDepth = 0;
        
        /**
         * <code>m_monitor</code> the progress monitor
         */
        private IProgressMonitor m_monitor;
        
        /**
         * @param monitor the progress monitor to use
         */
        public StepCounter(IProgressMonitor monitor) {
            m_monitor = monitor;
        }

        /**
         * {@inheritDoc}
         */
        public void nextCap(ICapPO cap) {
            if (m_currentEventStackDepth > 0) {
                m_eventHandlerSteps++;
            } else {
                m_monitor.worked(1);
            }
            m_totalSteps++;
        }

        /**
         * {@inheritDoc}
         */
        public void nextDataSetIteration() {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        public void retryCap(ICapPO cap) {
            if (m_currentEventStackDepth <= 0) {
                m_retriedSteps++;
            } else {
                m_eventHandlerSteps++;
            }
            m_totalSteps++;
        }

        /**
         * {@inheritDoc}
         */
        public void stackDecremented() {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        public void stackIncremented(INodePO node) {
            // Do nothing
        }
        
        /**
         * {@inheritDoc}
         */
        public void eventStackDecremented() {
            m_currentEventStackDepth--;
        }

        /**
         * {@inheritDoc}
         */
        public void eventStackIncremented() {
            m_currentEventStackDepth++;
        }

        /**
         * @return the total number of steps executed
         */
        public int getTotalSteps() {
            return m_totalSteps;
        }

        /**
         * @return the number of steps executed within event handlers
         */
        public int getEventHandlerSteps() {
            return m_eventHandlerSteps;
        }

        /**
         * @return the number of retried steps
         */
        public int getRetriedSteps() {
            return m_retriedSteps;
        }

        /**
         * @return the failedSteps
         */
        public int getFailedSteps() {
            return m_failedSteps;
        }
        
        /** increment the number of failed steps */
        public void incrementNumberOfFailedSteps() {
            m_failedSteps++;
        }
    }
    
    /**
     * IPostExecutionCommand to execute an external command 
     * if "CompSystem.RunLocal" is <code>true</code>.
     *
     * @author BREDEX GmbH
     * @created Sep 11, 2007
     */
    public class CommandExecutorCmd extends AbstractPostExecutionCommand {
        
        /**
         * Constructor
         */
        public CommandExecutorCmd() {
            super();
        }
        
        /**
         * {@inheritDoc}
         */
        public TestErrorEvent execute() throws JBException {
            IParamDescriptionPO desc = 
                m_currentCap.getParameterForUniqueId("CompSystem.RunLocal"); //$NON-NLS-1$
            try {
                ITDManager tdManager = 
                    m_externalTestDataBP.getExternalCheckedTDManager(
                            m_currentCap);
                ITestDataPO date = 
                    tdManager.getCell(0, desc);
                String runLocal = this.getValueForParam(date, m_currentCap, 
                    desc);
                if (Boolean.valueOf(runLocal)) {
                    // Execute script
                    desc = 
                        m_currentCap.getParameterForUniqueId("CompSystem.Command"); //$NON-NLS-1$
                    date = 
                        tdManager.getCell(0, desc);
                    String cmd = this.getValueForParam(date, m_currentCap, 
                        desc);
                    
                    desc = m_currentCap
                            .getParameterForUniqueId(COMP_SYSTEM_TIMEOUT);
                    date = 
                        tdManager.getCell(0, desc);
                    int timeout = Integer.parseInt(
                        this.getValueForParam(date, m_currentCap, 
                            desc));
                    
                    desc = 
                        m_currentCap.getParameterForUniqueId("CompSystem.ExpectedExitCode"); //$NON-NLS-1$
                    date = 
                        tdManager.getCell(0, desc);
                    int expectedExitCode = Integer.parseInt(
                        this.getValueForParam(date, m_currentCap, 
                            desc));

                    File dataDir = ExternalTestDataBP.getDataDir();
                    
                    MonitorTask mt = 
                        new ExternalCommandExecutor().executeCommand(
                            dataDir, cmd, timeout);
                    
                    if (!mt.wasCmdValid()) {
                        
                        return EventFactory.createActionError(
                                TestErrorEvent.NO_SUCH_COMMAND);
                    }
                    
                    if (mt.hasTimeoutOccurred()) {
                        return EventFactory.createActionError(
                                TestErrorEvent.CONFIRMATION_TIMEOUT);
                    }
                    
                    int actualExitValue = mt.getExitCode();
                    if (actualExitValue != expectedExitCode) {
                        return EventFactory.createVerifyFailed(
                                String.valueOf(expectedExitCode), 
                                String.valueOf(actualExitValue));
                    }

                }
            } catch (IllegalArgumentException e) {
                throw new JBException("IllegalArgumentException", e, //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new JBException("InvalidDataException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } 
            
            return null;
        }
    }
  
    /**
     * @return variableStore
     */
    public TDVariableStore getVariableStore() {
        return m_varStore;
    }
    
    /**
     * 
     * @return the number of test steps that have been executed during this 
     *         test.
     */
    public int getNumberOfTestedSteps() {
        return m_stepCounter.getTotalSteps();
    }

    /**
     * 
     * @return the number of test steps that would be executed during this test
     *         provided the test does not end prematurely and no event handler 
     *         test steps are executed.
     */
    public int getExpectedNumberOfSteps() {
        return m_expectedNumberOfSteps;
    }

    /**
     * 
     * @return the number of test steps that have been executed from within an
     *         event handler during this test.
     */
    public int getNumberOfEventHandlerSteps() {
        return m_stepCounter.getEventHandlerSteps();
    }

    /**
     * 
     * @return the number of test steps that have been retried during 
     *         this test.
     */
    public int getNumberOfRetriedSteps() {
        return m_stepCounter.getRetriedSteps();
    }
    
    /**
     * 
     * @return the number of test steps that have failed
     */
    public int getNumberOfFailedSteps() {
        return m_stepCounter.getFailedSteps();
    }

    /**
     * @param timerStore the timerStore to set
     */
    public void setTimerStore(Map<String, Long> timerStore) {
        m_timerStore = timerStore;
    }

    /**
     * @return the timerStore
     */
    public Map<String, Long> getTimerStore() {
        return m_timerStore;
    }

    /**
     * @param paused the paused to set
     */
    private void setPaused(boolean paused) {
        m_paused = paused;
    }

    /**
     * @param noRunMode String noRun option mode
     * @param step current step of noRun execution
     * @return true is no run execution must be finished
     * return false if test run without no-run option
     * or the last step of no run execution is not jet reached
     */
    public static boolean shouldExecutionStop(String noRunMode,
            TestExecutionConstants.RunSteps step) {
        if (StringUtils.isEmpty(noRunMode)) {
            return false;
        }
        return noRunMode.equals(step.getStepValue());
    }
}