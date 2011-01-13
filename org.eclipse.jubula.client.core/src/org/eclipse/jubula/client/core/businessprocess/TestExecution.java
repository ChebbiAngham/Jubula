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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jubula.client.core.ClientTestFactory;
import org.eclipse.jubula.client.core.IClientTest;
import org.eclipse.jubula.client.core.MessageFactory;
import org.eclipse.jubula.client.core.agent.AutAgentRegistration;
import org.eclipse.jubula.client.core.agent.AutRegistrationEvent;
import org.eclipse.jubula.client.core.agent.AutRegistrationEvent.RegistrationStatus;
import org.eclipse.jubula.client.core.agent.IAutRegistrationListener;
import org.eclipse.jubula.client.core.commands.CAPTestResponseCommand;
import org.eclipse.jubula.client.core.commands.DisplayManualTestStepResponseCommand;
import org.eclipse.jubula.client.core.commands.EndTestExecutionResponseCommand;
import org.eclipse.jubula.client.core.commands.TakeScreenshotResponseCommand;
import org.eclipse.jubula.client.core.communication.AUTConnection;
import org.eclipse.jubula.client.core.communication.BaseConnection.GuiDancerNotConnectedException;
import org.eclipse.jubula.client.core.communication.ConnectionException;
import org.eclipse.jubula.client.core.communication.ServerConnection;
import org.eclipse.jubula.client.core.model.GuiDancerLogicComponentNotManagedException;
import org.eclipse.jubula.client.core.model.IAUTConfigPO;
import org.eclipse.jubula.client.core.model.IAUTConfigPO.ActivationMethod;
import org.eclipse.jubula.client.core.model.IAUTMainPO;
import org.eclipse.jubula.client.core.model.ICapPO;
import org.eclipse.jubula.client.core.model.IEventStackModificationListener;
import org.eclipse.jubula.client.core.model.IExecStackModificationListener;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IObjectMappingPO;
import org.eclipse.jubula.client.core.model.IParamDescriptionPO;
import org.eclipse.jubula.client.core.model.ITDManagerPO;
import org.eclipse.jubula.client.core.model.ITestDataPO;
import org.eclipse.jubula.client.core.model.ITestJobPO;
import org.eclipse.jubula.client.core.model.ITestSuitePO;
import org.eclipse.jubula.client.core.model.ReentryProperty;
import org.eclipse.jubula.client.core.model.ResultTreeBuilder;
import org.eclipse.jubula.client.core.model.ResultTreeTracker;
import org.eclipse.jubula.client.core.model.TestResult;
import org.eclipse.jubula.client.core.model.TestResultNode;
import org.eclipse.jubula.client.core.persistence.GeneralStorage;
import org.eclipse.jubula.client.core.persistence.Hibernator;
import org.eclipse.jubula.client.core.utils.ExecObject;
import org.eclipse.jubula.client.core.utils.ModelParamValueConverter;
import org.eclipse.jubula.client.core.utils.ParamValueConverter;
import org.eclipse.jubula.client.core.utils.Traverser;
import org.eclipse.jubula.communication.ICommand;
import org.eclipse.jubula.communication.message.ActivateApplicationMessage;
import org.eclipse.jubula.communication.message.CAPTestMessage;
import org.eclipse.jubula.communication.message.CAPTestResponseMessage;
import org.eclipse.jubula.communication.message.DisplayManualTestStepMessage;
import org.eclipse.jubula.communication.message.EndTestExecutionMessage;
import org.eclipse.jubula.communication.message.InitTestExecutionMessage;
import org.eclipse.jubula.communication.message.Message;
import org.eclipse.jubula.communication.message.MessageCap;
import org.eclipse.jubula.communication.message.MessageParam;
import org.eclipse.jubula.communication.message.NullMessage;
import org.eclipse.jubula.communication.message.ResetMonitoringDataMessage;
import org.eclipse.jubula.communication.message.RestartAutMessage;
import org.eclipse.jubula.communication.message.TakeScreenshotMessage;
import org.eclipse.jubula.toolkit.common.xml.businessprocess.ComponentBuilder;
import org.eclipse.jubula.tools.constants.StringConstants;
import org.eclipse.jubula.tools.constants.TimeoutConstants;
import org.eclipse.jubula.tools.constants.TimingConstantsClient;
import org.eclipse.jubula.tools.exception.CommunicationException;
import org.eclipse.jubula.tools.exception.GDException;
import org.eclipse.jubula.tools.exception.InvalidDataException;
import org.eclipse.jubula.tools.i18n.I18n;
import org.eclipse.jubula.tools.messagehandling.MessageIDs;
import org.eclipse.jubula.tools.objects.IComponentIdentifier;
import org.eclipse.jubula.tools.objects.event.EventFactory;
import org.eclipse.jubula.tools.objects.event.TestErrorEvent;
import org.eclipse.jubula.tools.registration.AutIdentifier;
import org.eclipse.jubula.tools.utils.ExternalCommandExecutor;
import org.eclipse.jubula.tools.utils.ExternalCommandExecutor.MonitorTask;
import org.eclipse.jubula.tools.utils.StringParsing;
import org.eclipse.jubula.tools.utils.TimeUtil;
import org.eclipse.jubula.tools.xml.businessmodell.Action;
import org.eclipse.jubula.tools.xml.businessmodell.CompSystem;
import org.eclipse.jubula.tools.xml.businessmodell.Component;
import org.eclipse.jubula.tools.xml.businessmodell.Param;
import org.osgi.framework.Constants;



/**
 * This class creates the captestmessage with test data and sends this message
 * to the server. Then it waits for an answer of the server and processes the
 * answer. Then it sends the next captestmessage.
 * 
 * @author BREDEX GmbH
 * @created 03.09.2004
 */
public class TestExecution {
    /**
     * <code>COM_BREDEXSW_GUIDANCER_CLIENT_TEST</code>
     */
    private static final String CLIENT_TEST_PLUGIN_ID = 
        "org.eclipse.jubula.client.core"; //$NON-NLS-1$

    /** The logger */
    private static final Log LOG = LogFactory.getLog(TestExecution.class);

    /** 
     * Constant for the m_varStore of the last return value of the last
     * executed Action 
     */
    private static final String LAST_ACTION_RETURN = "GD_LAR"; //$NON-NLS-1$
    
    /** 
     * Constant for the m_varStore of the last return value of the current
     * data set number
     */
    private static final String CURRENT_DATASET_NUMBER = "GD_CDN"; //$NON-NLS-1$
    
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
    private StepCounter m_stepCounter = new StepCounter();
    
    /** the current AUTConfig */
    private IAUTConfigPO m_autConfig;
    
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
        ClientTestFactory.getClientTest().addTestExecutionEventListener(
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
     */
    public void executeTestSuite(ITestSuitePO testSuite, Locale locale, 
        AutIdentifier autId, boolean autoScreenshot,
        Map<String, String> externalVars) {
        
        final IProgressMonitor monitor = new NullProgressMonitor();
        m_stopped = false;
        synchronized (this) {
            if (m_stopped) {
                // Test execution was stopped in another thread.
                // Just return without performing any additional test 
                // execution initialization.
                return;
            }
            ClientTestFactory.getClientTest().addTestExecutionEventListener(
                    new ITestExecutionEventListener() {
                        public void endTestExecution() {
                            try {
                                AUTConnection.getInstance().close();
                            } catch (ConnectionException e) {
                                // Do nothing. Connection is already closed.
                            }
                            ClientTestFactory.getClientTest()
                                .removeTestExecutionEventListener(this);
                            monitor.setCanceled(true);
                        }
                        
                        public void stateChanged(TestExecutionEvent event) {
                            // Do nothing
                        }
                    });
        }
        
        m_autConfig = ClientTestFactory.getClientTest().getLastAutConfig();
        m_autoScreenshot = autoScreenshot;
        setPaused(false);
        Validate.notNull(testSuite, "Testsuite must not be null"); //$NON-NLS-1$
        m_executionLanguage = locale;
        m_varStore.storeEnvironmentVariables();
        storePredefinedVariables(m_varStore, testSuite);
        storeExternallyDefinedVariables(m_varStore, externalVars);
        
        m_externalTestDataBP.clearExternalData();

        try {
            if (m_stopped) {
                // Test execution already stopped. No need to continue.
                return;
            }
            if (AUTConnection.getInstance().connectToAut(autId, monitor)) {
                startTestSuite(testSuite, locale);
            } else {
                handleNoConnectionToAUT(testSuite, autId);
            }
        } catch (ConnectionException e) {
            LOG.error("Unable to connect to AUT.", e); //$NON-NLS-1$
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
            autName = I18n.getString("ErrorDetail.NO_AUT_ID_FOR_REF_TS_FOUND", //$NON-NLS-1$
                    new String[] { testSuite.getName() });
        }
        ClientTestFactory.getClientTest().fireTestExecutionChanged(
                new TestExecutionEvent(TestExecutionEvent.TEST_EXEC_FAILED,
                        new GDException("Could not connect to AUT: " + autName, //$NON-NLS-1$
                                MessageIDs.E_NO_AUT_CONNECTION_ERROR)));
    }
    
    /**
     * @param autName the AUT Id
     * @return wheter the aut name is correctly set
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

        // gd.language
        varStore.store(TDVariableStore.VAR_LANG, 
                m_executionLanguage.toString());

        // gd.testsuite
        varStore.store(TDVariableStore.VAR_TS, testSuite.getName());

        // gd.username
        varStore.store(TDVariableStore.VAR_USERNAME, 
                System.getProperty("user.name")); //$NON-NLS-1$
        
        // gd.dbusername
        varStore.store(TDVariableStore.VAR_DBUSERNAME, 
                Hibernator.instance().getCurrentDBUser());
        
        try {
            ServerConnection serverConn = ServerConnection.getInstance();

            // gd.autstarter
            varStore.store(TDVariableStore.VAR_AUTSTARTER, 
                    serverConn.getCommunicator().getHostName());
            
            // gd.portnumber
            varStore.store(TDVariableStore.VAR_PORT, 
                    String.valueOf(serverConn.getCommunicator().getPort()));

        } catch (ConnectionException ce) {
            // No connection to AutStarter.
            // Do nothing.
        }
        
        // gd.aut
        varStore.store(TDVariableStore.VAR_AUT, testSuite.getAut().getName());
        
        // gd.autconfig
        if (m_autConfig != null) {
            varStore.store(TDVariableStore.VAR_AUTCONFIG, 
                    m_autConfig.getName());
        } else {
            // write constant for AUTs which has been started via gdrun
            varStore.store(TDVariableStore.VAR_AUTCONFIG, 
                    TestresultSummaryBP.GDRUN);
        }

        // gd.clientVersion
        varStore.store(TDVariableStore.VAR_CLIENTVERSION, 
                (String)Platform.getBundle(
                        CLIENT_TEST_PLUGIN_ID).getHeaders().get(
                                Constants.BUNDLE_VERSION));
        
    }

    /**
     * @param testSuite testSuite
     * @param locale language valid for testexecution
     */
    private void startTestSuite(ITestSuitePO testSuite, Locale locale) {
        Validate.notNull(testSuite, "No testsuite available"); //$NON-NLS-1$
        ICapPO firstCap = null;
        m_expectedNumberOfSteps = 0;
        m_trav = new Traverser(testSuite, locale);
        m_stepCounter.reset();
        IClientTest clientTest = ClientTestFactory.getClientTest();
        clientTest.setLastConnectedAutId(
                getConnectedAutId().getExecutableName());       
//        Map m = clientTest.getLastConnectedAutConfigMap();
//        boolean resetMonitoringData = Boolean.valueOf(
//                (String)m.get(MonitoringConstants.RESET_MONITORING_DATA));
//        boolean monitoring = clientTest.isRunningWithMonitoring();
//        if (resetMonitoringData && monitoring) {
//            resetMonitoringData();
//        }
        try {
            // build and show result Tree
            Traverser copier = new Traverser(testSuite, locale);
            ResultTreeBuilder resultTreeBuilder = new ResultTreeBuilder(copier);
            copier.addExecStackModificationListener(resultTreeBuilder);
            ICapPO iterNode = copier.next();
            while (iterNode != null) {
                iterNode = copier.next();
                m_expectedNumberOfSteps++;
            }
            // end build tree
            TestResultBP.getInstance().setResultTestModel(
                    new TestResult(GeneralStorage.getInstance().getProject(),
                    resultTreeBuilder.getRootNode()));
            initTestExecutionMessage();
            if (LOG.isInfoEnabled()) {
                LOG.info("Start TestSuite: " + testSuite.getName()); //$NON-NLS-1$
            }
            
            m_resultTreeTracker = new ResultTreeTracker(resultTreeBuilder.
                    getRootNode(), m_externalTestDataBP);
            m_trav.addExecStackModificationListener(m_resultTreeTracker);
            m_trav.addEventStackModificationListener(m_stepCounter);
            m_trav.addExecStackModificationListener(m_stepCounter);
            
            ClientTestFactory.getClientTest().
                fireTestExecutionChanged(new TestExecutionEvent(
                        TestExecutionEvent.TEST_EXEC_RESULT_TREE_READY));
            firstCap = m_trav.next();
        } catch (GDException e) {
            LOG.error("Incomplete testdata", e); //$NON-NLS-1$
            fireError(e);
        }
        if (firstCap != null) {
            ClientTestFactory.getClientTest().
                fireTestExecutionChanged(new TestExecutionEvent(
                        TestExecutionEvent.TEST_EXEC_START));
            processCap(firstCap);
        } else {
            endTestExecution();
        }
    }

    /**
     * Checks if the given CAP is executable.<br>
     * It tries to build a capMessage. If this method does not catch an
     * exception, the given cap is executable because a complete cap message
     * was built.
     * <b>This method should only be called if an 
     * incomplete TestSuite is running!</b>
     * @param cap the cap to check
     * @return <code>null</code> if everything is OK, a thrown Exception otherwise.
     */
    private GDException isCapExecutable(ICapPO cap) {
        try {
            buildMessageCap(cap, true);
        } catch (GuiDancerLogicComponentNotManagedException e) {
            return new GDException("Incomplete TS-run: missing Object Mapping",  //$NON-NLS-1$
                TestExecutionEvent.TEST_RUN_INCOMPLETE_OBJECTMAPPING_ERROR);
        } catch (InvalidDataException ide) {
            return new GDException("Incomplete TS-run: missing Test Data",  //$NON-NLS-1$
                TestExecutionEvent.TEST_RUN_INCOMPLETE_TESTDATA_ERROR);
        } catch (IndexOutOfBoundsException iobe) {
            return new GDException("Incomplete TS-run: missing Test Data",  //$NON-NLS-1$
                TestExecutionEvent.TEST_RUN_INCOMPLETE_TESTDATA_ERROR);
        }
        return null;
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
                LOG.debug("TestStep: " + currCap.getName()); //$NON-NLS-1$
                LOG.debug("Component: " + currCap.getComponentName()); //$NON-NLS-1$                
            }
            messageCap = buildMessageCap(currCap, false);
            if (!m_stopped) {
                CAPTestMessage capTestMessage = MessageFactory
                        .getCAPTestMessage(messageCap);
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
        } catch (GuiDancerNotConnectedException bnce) {
            LOG.error("AUTConnection fails", bnce); //$NON-NLS-1$
        } catch (CommunicationException bce) {
            LOG.error("Communication with AUT fails", bce); //$NON-NLS-1$
            fireError(bce);
        } catch (GuiDancerLogicComponentNotManagedException blcnme) {
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
            m_currentCap.getParameterForUniqueId("CompSystem.Timeout"); //$NON-NLS-1$
        timeOuts.add(m_currentCap.getParameterList().indexOf(desc1));
        desc1 = m_currentCap.getParameterForUniqueId("CompSystem.TimeMillSec"); //$NON-NLS-1$
        timeOuts.add(m_currentCap.getParameterList().indexOf(desc1));
        
        int timeout = 0;
        for (int index : timeOuts) {
            if (index > -1) {
                final MessageParam param = (MessageParam)messageCap
                    .getMessageParams().get(index);
                final String paramTimeOut = param.getValue();
                timeout += Integer.parseInt(paramTimeOut);
            }
        }
        
        // Special handling for Show Text
        boolean isShowText = messageCap.getMethod().equals("gdShowText"); //$NON-NLS-1$
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
            LOG.debug("Executing ClientAction: "  //$NON-NLS-1$
                + action.getPostExecutionCommand());
        }
        final String postExecCommandClass = action.getPostExecutionCommand();
        final IPostExecutionCommand command = m_postExecCmdFactory
            .createCommand(postExecCommandClass);
        try {
            TestErrorEvent errorEvent = command.execute();
            
            if (errorEvent != null) { 
                CAPTestResponseMessage response = new CAPTestResponseMessage();
                response.setTestErrorEvent(errorEvent);
                response.setMessageCap(capTestMessage.getMessageCap());
                return response;
            }
        } catch (GDException e) { // NOPMD by al on 3/19/07 1:24 PM
            // nothing
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
            (MessageParam)messageCap.getMessageParams().get(0);
        MessageParam timePerWordParam = 
            (MessageParam)messageCap.getMessageParams().get(2);
        try {
            int numWords = StringParsing.countWords(textParam.getValue());
            return Integer.parseInt(timePerWordParam.getValue()) 
                * numWords;
        } catch (NumberFormatException e) {
            LOG.warn("Error while parsing timeout parameter. " //$NON-NLS-1$
                + "Using default value.", e); //$NON-NLS-1$
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
     * @throws GuiDancerLogicComponentNotManagedException
     *             if component not found in objectMap.
     *             if building compSystem fails.
     *             if component cannot be found.
     * @throws InvalidDataException in case of "run incomplete"-mode
     * and missing test data.
     */
    private MessageCap buildMessageCap(ICapPO cap, boolean runIncomplete)
        throws InvalidDataException, 
        GuiDancerLogicComponentNotManagedException {
        
        MessageCap messageCap;
        String logicalName = null;
        try {
            messageCap = new MessageCap();
            CompSystem compSystem = ComponentBuilder.getInstance()
                .getCompSystem();
            m_currentCap = cap;
            ITestSuitePO ts = (ITestSuitePO)m_trav.getRoot();
            IAUTMainPO aut = ts.getAut();
            IObjectMappingPO om = aut.getObjMap();
            IObjectMappingPO transientOm = ObjectMappingEventDispatcher.
                getObjMapTransient();
            // Find the component name. It may be overriden in one or
            // more ExecTestCase nodes.
            logicalName = m_compNamesBP.findCompName(
                    m_trav.getExecStackAsNodeList(), cap, 
                    cap.getComponentName(),
                    ComponentNamesBP.getInstance()).getCompName();
            messageCap.setResolvedLogicalName(logicalName);
            IComponentIdentifier technicalName;
            
            try {
                technicalName = transientOm.getTechnicalName(logicalName);
            } catch (GuiDancerLogicComponentNotManagedException e) {
                technicalName = om.getTechnicalName(logicalName);
            }
            if (technicalName == null) {
                throw new GuiDancerLogicComponentNotManagedException(
                        StringConstants.EMPTY,
                        MessageIDs.E_COMPONENT_NOT_MANAGED);
            }
            Component comp = compSystem.findComponent(cap.getComponentType());
            Action action = comp.findAction(cap.getActionName());
            messageCap.setMethod(action.getMethod());
            messageCap.setPostExecutionCommand(
                action.getPostExecutionCommand());
            messageCap.setCi(technicalName);
            if (cap.getParameterList() != null) {
                messageCap = configureMessageCap(cap, messageCap, action, 
                    runIncomplete);
            }
            return messageCap;
        } catch (GuiDancerLogicComponentNotManagedException blcnme) {
            LOG.error("No entry for " + cap.getComponentName() //$NON-NLS-1$
                + "(=professional name) / " + logicalName //$NON-NLS-1$
                + "(=technical name)", //$NON-NLS-1$
                blcnme);
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
        ITDManagerPO tdManager = null;
        try {
            tdManager = m_externalTestDataBP.getExternalCheckedTDManager(cap);
        } catch (GDException gde) {
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
                Validate.notNull(date, "No testdata available for CAP: " + //$NON-NLS-1$
                    cap.getName() + ", parameter: " +  //$NON-NLS-1$ 
                    desc.getName() + ", and dataset number 0"); //$NON-NLS-1$
                String value = null;
                try {
                    final int dsNumber = m_trav.getDataSetNumber();
                    m_varStore.store(CURRENT_DATASET_NUMBER, String.valueOf(
                        dsNumber + 1)); // 1-based for the user!
                    ParamValueConverter conv = new ModelParamValueConverter(
                        date.getValue().getValue(getLocale()), cap,
                        getLocale(), desc);
                    List <ExecObject> stackList = 
                        new ArrayList<ExecObject>(m_trav.getExecStackAsList());
                    value = conv.getExecutionString(stackList, getLocale());
                } catch (InvalidDataException e) {
                    if (!runIncomplete) {
                        LOG.error("No value available for parameter: "  //$NON-NLS-1$
                            + desc.getName() 
                            + " in node: " + cap.getName(), e); //$NON-NLS-1$)
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
            LOG.debug("Param: " + xmlParam.getName()); //$NON-NLS-1$            
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
        Thread t = new Thread("Execute Test Step") { //$NON-NLS-1$
            public void run() {
                ICapPO nextCap = null;
                processPostExecution(msg);
                TestResultNode resultNode = m_resultTreeTracker.getEndNode();
                resultNode.setComponentName(
                    ComponentNamesBP.getInstance().getName(
                        msg.getMessageCap().getResolvedLogicalName(), 
                        GeneralStorage.getInstance().getProject().getId()));
                final boolean testOk = !msg.hasTestErrorEvent();
                if (msg.getState() == CAPTestResponseMessage.PAUSE_EXECUTION) {
                    pauseExecution();
                }
                if (testOk) {
                    resultNode.setResult(m_trav.getSuccessResult(), null);
                    try {
                        if (!m_stopped) {
                            nextCap = m_trav.next();
                        }
                    } catch (GDException e) {
                        LOG.error("Incomplete testdata", e); //$NON-NLS-1$
                        fireError(e);
                    }
                } else {
                    // ErrorEvent has occured
                    TestErrorEvent event = msg.getTestErrorEvent();
                    String eventType = event.getId();

                    if (m_trav.getEventHandlerReentry(eventType).equals(
                            ReentryProperty.RETRY)) {
                        resultNode.setResult(TestResultNode.RETRYING, event);
                    } else {
                        m_stepCounter.incrementNumberOfFailedSteps();
                        resultNode.setResult(TestResultNode.ERROR, event);
                        if (m_autoScreenshot) {
                            addScreenshot(resultNode);
                        }
                        if (ClientTestFactory.getClientTest()
                                .isPauseTestExecutionOnError()) {
                            pauseExecution();
                        }
                    }
                    try {
                        if (!m_stopped) {
                            nextCap = m_trav.next(eventType);
                        }
                    } catch (GDException e) {
                        LOG.error("Incomplete testdata", e); //$NON-NLS-1$
                        fireError(e);
                    }
                }
                if (nextCap != null) {
                    while (isPaused()) {
                        try {
                            testConnection();
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Thread interrupted.", e); //$NON-NLS-1$
                            }
                            return;
                        }
                    }
                    processCap(nextCap);
                } else {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Testsuite finished."); //$NON-NLS-1$
                    }
                    endTestExecution();
                }
            }
        };
        t.start();
    }

    /**
     * @param resultNode the result node to add the screenshot for
     */
    private void addScreenshot(TestResultNode resultNode) {
        // Send request to aut starter and wait for response
        ICommand command = new TakeScreenshotResponseCommand(resultNode);
        Message message = new TakeScreenshotMessage();
        try {
            AUTConnection.getInstance().request(
                    message, command, 
                    TimeoutConstants.CLIENT_SERVER_TIMEOUT_TAKE_SCREENSHOT);
        } catch (GuiDancerNotConnectedException nce) {
            if (LOG.isErrorEnabled()) {
                LOG.error(nce);
            }
        } catch (CommunicationException ce) {
            if (LOG.isErrorEnabled()) {
                LOG.error(ce);
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
            
            TestErrorEvent errorEvent = executePostExecCommand(cmdClassName); 
            if (msg.getTestErrorEvent() == null 
                && errorEvent != null) { 
                
                msg.setTestErrorEvent(errorEvent); 
            }
        }
    }
    
    /**
     * Loads, instantiates and executes the given IPostExecutionCommand class
     * @param cmdClassName the IPostExecutionCommand to execute.
     * @return a TestErrorEvent representing an error that occurred during  
     *         execution, or <code>null</code> if no such error occurs. 
     */
    private TestErrorEvent executePostExecCommand(String cmdClassName) {
        
        final IPostExecutionCommand cmd = m_postExecCmdFactory
            .createCommand(cmdClassName);
        try {
            return cmd.execute();
        } catch (GDException e) {
            LOG.error("Error executing command: " + cmdClassName  //$NON-NLS-1$
                + " . Exception: " + String.valueOf(e)); //$NON-NLS-1$
            fireError(e);
            return null;
        }
    }
    

    /**
     * Tests the connection to server (sends a NullMessage to server)
     * 
     */
    private void testConnection() {
        try {
            AUTConnection.getInstance().send(new NullMessage());
        } catch (CommunicationException e) {
            fireError(new GDException(MessageIDs.getMessage(
                    MessageIDs.E_INTERRUPTED_CONNECTION), 
                    MessageIDs.E_INTERRUPTED_CONNECTION));
        }
    }

    /**
     * Sends a init test execution message
     */
    private void initTestExecutionMessage() {
        try {
            InitTestExecutionMessage msg = new InitTestExecutionMessage();
            // FIXME key "ACTIVATION_METHOD" should NOT be fix!
            if (m_autConfig != null) {
                final ActivationMethod activateMethod = 
                        ActivationMethod.valueOf(
                                m_autConfig.getValue("ACTIVATION_METHOD",  //$NON-NLS-1$
                                        ActivationMethod.NONE.name())
                                            .toUpperCase());
                if (activateMethod != null) {
                    msg.setDefaultActivationMethod(activateMethod.name());
                }
            }
            AUTConnection.getInstance().send(msg);
        } catch (CommunicationException exc) {
            fireError(exc);
        }
    }
    
    /**
     * Sends an activate message to the AUT
     * 
     */
    private void sendActivateAUTMessage() {
        try {
            ActivateApplicationMessage msg = MessageFactory
                .getActivateApplicationMessage();
            AUTConnection.getInstance().send(msg);
        } catch (CommunicationException exc) {
            fireError(exc);
        }
    }

    /**
     * Fires an event if test fails
     * @param e GDException
     */
    private void fireError(Exception e) {
        ClientTestFactory.getClientTest().
            fireTestExecutionChanged(new TestExecutionEvent(
                TestExecutionEvent.TEST_EXEC_FAILED, e));
        endTestExecution();
    }

    /**
     * Fires an event if test fails, because the component name is wrong.
     */
    private void fireComponentError() {
        ClientTestFactory.getClientTest().
            fireTestExecutionChanged(new TestExecutionEvent(
                TestExecutionEvent.TEST_EXEC_COMPONENT_FAILED));
        ClientTestFactory.getClientTest().fireEndTestExecution();
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
                    LOG.info("Testsuite is stopped"); //$NON-NLS-1$
                }
                
                ClientTestFactory.getClientTest().fireEndTestExecution();
                try {
                    AUTConnection.getInstance().close();
                } catch (ConnectionException e) {
                    // Do nothing. Connection already closed.
                }
            }
        }
    }
    
    /**
     * This method will reset the profiling agent. Actually the message will
     * invoke the rest metod.
     * 
     */
    public void resetMonitoringData() {
             
        try {
            ResetMonitoringDataMessage message = 
                new ResetMonitoringDataMessage(AUTConnection.getInstance()
                        .getConnectedAutId().getExecutableName());
            ServerConnection.getInstance().send(message);

        } catch (GuiDancerNotConnectedException nce) {
            LOG.error(nce);
            
        } catch (CommunicationException ce) {
            LOG.error(ce);
           
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
        } catch (GuiDancerNotConnectedException nce) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(nce);
            }
            stopExecution();
        } catch (CommunicationException ce) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(ce);
            }
            stopExecution();
        }
    }

    /**
     * Toggles the pause state of the test execution
     */
    public void pauseExecution() {
        setPaused(!isPaused());
        if (isPaused()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Testsuite is paused"); //$NON-NLS-1$
            }
            ClientTestFactory.getClientTest().fireTestExecutionChanged(
                new TestExecutionEvent(TestExecutionEvent.TEST_EXEC_PAUSED));
        } else {
            if (LOG.isInfoEnabled()) {
                LOG.info("Testexecution has resumed"); //$NON-NLS-1$
            }
            // FIXME key "ACTIVATE_APPLICATION" should NOT be fix!
            if (m_autConfig != null) {
                if (Boolean.valueOf(m_autConfig.getValue("ACTIVATE_APPLICATION",  //$NON-NLS-1$
                        StringConstants.EMPTY))) {
                    sendActivateAUTMessage();
                }
            }
            ClientTestFactory.getClientTest().fireTestExecutionChanged(
                new TestExecutionEvent(TestExecutionEvent.TEST_EXEC_START));
        }
    }
    
    /**
     * Pauses the TestExecution.
     * @param paused if true, the execution pauses, 
     * if false, the execution continues
     * 
     */
    public void pauseExecution(boolean paused) {
        if (isPaused() != paused) {
            pauseExecution();
        }
    }
    
    /**
     * timeout()
     */
    public void timeout() {
        m_resultTreeTracker.getEndNode().setResult(TestResultNode.ABORT, null);
        fireError(new GDException(MessageIDs.getMessage(MessageIDs.
                E_TIMEOUT_CONNECTION), MessageIDs.E_TIMEOUT_CONNECTION));
    }

    /**
     * @return Returns the actualCap.
     */
    public ICapPO getActualCap() {
        return m_currentCap;
    }
    
    /**
     * get hostname of localhost
     * 
     * @return aut hostname
     */
    public String getLocalHostname() {
        String hostname = StringConstants.EMPTY;
        if (m_autConfig != null) {
            hostname = m_autConfig.getServer();
            if (hostname.equals("localhost")) { //$NON-NLS-1$
                InetAddress addr;
                try {
                    addr = InetAddress.getLocalHost();
                    hostname = addr.getHostName();
                } catch (UnknownHostException e) {
                    // ignore
                }
            }
        } else {
            hostname = TestresultSummaryBP.GDRUN;
        }
        return hostname;
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
     * 
     * @return true if Test Suite is paused
     */
    public boolean isPaused() {
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
        public TestErrorEvent execute() throws GDException {
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
                ITDManagerPO tdManager = 
                    m_externalTestDataBP.getExternalCheckedTDManager(
                            m_currentCap);
                ITestDataPO date = 
                    tdManager.getCell(0, desc);
                String varName = this.getValueForParam(date, m_currentCap, 
                    desc);
                m_varStore.store(varName, m_varStore.getValue(
                    LAST_ACTION_RETURN));
                return null;
            } catch (IllegalArgumentException e) {
                throw new GDException("IllegalArgumentException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new GDException("InvalidDataException", e,  // //$NON-NLS-1$
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
        protected String getTimerName() throws GDException {
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
        public TestErrorEvent execute() throws GDException {
            try {
                String timerName = getTimerName();
                String variableName = getValueForParam(
                        "CompSystem.VariableToStoreAbsoluteStartTime"); //$NON-NLS-1$
                
                Long curTimeInMillisecs = 
                    new Long(System.currentTimeMillis());
                
                getTimerStore().put(timerName, curTimeInMillisecs);
                m_varStore.store(variableName, curTimeInMillisecs.toString());
            } catch (IllegalArgumentException e) {
                throw new GDException("IllegalArgumentException", e, //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new GDException("InvalidDataException", e,  // //$NON-NLS-1$
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
        public TestErrorEvent execute() throws GDException {
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
                throw new GDException("IllegalArgumentException", e, //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new GDException("InvalidDataException", e,  // //$NON-NLS-1$
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
        public TestErrorEvent execute() throws GDException {
            try {
                String actionToPerform = 
                    getValueForParam("CompSystem.ActionToPerfom"); //$NON-NLS-1$
                String expectedBehavior = 
                    getValueForParam("CompSystem.ExpectedBehavior"); //$NON-NLS-1$
                int timeout = Integer.parseInt(
                    getValueForParam("CompSystem.Timeout")); //$NON-NLS-1$
                
                Message message = new DisplayManualTestStepMessage(
                        actionToPerform, expectedBehavior, timeout);
                ICommand command = 
                    new DisplayManualTestStepResponseCommand(this);

                ServerConnection.getInstance()
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
                throw new GDException("IllegalArgumentException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new GDException("InvalidDataException", e,  // //$NON-NLS-1$
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
     * 
     * @author BREDEX GmbH
     * @created Aug 22, 2006
     */
    public class RestartCmd implements IPostExecutionCommand {

        /**
         * {@inheritDoc}
         */
        public TestErrorEvent execute() throws GDException {
            final AutIdentifier autId = getConnectedAutId();
            final AtomicBoolean isAutRestarted = new AtomicBoolean(false);
            IAutRegistrationListener registrationListener = 
                new IAutRegistrationListener() {
                    private boolean m_autEnded = false;
                    public void handleAutRegistration(
                            AutRegistrationEvent event) {
                        if (autId.equals(event.getAutId())) {
                            if (event.getStatus() 
                                    == RegistrationStatus.Deregister) {
                                m_autEnded = true;
                            }
                            if (m_autEnded && event.getStatus() 
                                    == RegistrationStatus.Register) {
                                isAutRestarted.set(true);
                            }
                        }
                    }
                };
            try {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // nothing
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Requesting AUT Agent to close AUTConnection..."); //$NON-NLS-1$
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
                ServerConnection.getInstance().send(
                        new RestartAutMessage(autId));
                while (!isAutRestarted.get()) {
                    // wait for aut registration
                    try {
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
                initTestExecutionMessage();
                return null;
            } finally {
                AutAgentRegistration.getInstance().removeListener(
                        registrationListener);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Continue test execution..."); //$NON-NLS-1$
                }
                if (!m_stopped) { // the AUT/TS may be stopped by a project
                                  // load
                    ClientTestFactory.getClientTest().fireTestExecutionChanged(
                            new TestExecutionEvent(
                                    TestExecutionEvent.TEST_EXEC_RESTART));
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Can't continue, TS is stopped..."); //$NON-NLS-1$
                    }
                }
            }
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
         * {@inheritDoc}
         */
        public void nextCap(ICapPO cap) {
            if (m_currentEventStackDepth > 0) {
                m_eventHandlerSteps++;
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
         * Reset all attributes and counters to their initial values.
         */
        public void reset() {
            resetCounter();
            m_currentEventStackDepth = 0;
        }
        
        /**
         * Reset all counters to their initial values.
         */
        private void resetCounter() {
            m_totalSteps = 0;
            m_eventHandlerSteps = 0;
            m_retriedSteps = 0;
            m_failedSteps = 0;
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
        public TestErrorEvent execute() throws GDException {
            IParamDescriptionPO desc = 
                m_currentCap.getParameterForUniqueId("CompSystem.RunLocal"); //$NON-NLS-1$
            try {
                ITDManagerPO tdManager = 
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
                    
                    desc = 
                        m_currentCap.getParameterForUniqueId("CompSystem.Timeout"); //$NON-NLS-1$
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
                throw new GDException("IllegalArgumentException", e, //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } catch (InvalidDataException e) {
                throw new GDException("InvalidDataException", e,  // //$NON-NLS-1$
                    MessageIDs.E_STEP_EXEC);
            } 
            
            return null;
        }
    }
  
    /**
     * Interface for commands to execute after the execution of the dependent
     * CAP. The implementing class of this interface must be inscribed 
     * with the full qualified name in the guidancerContext.xml 
     * in the "postExecutionCommand"-tag in the dependent Action.
     * 
     * @author BREDEX GmbH
     * @created 24.07.2006
     */
    public static interface IPostExecutionCommand {
        
        /**
         * Implementation of this IPostExecutionCommand
         * @throws GDException in case of error while execution
         * @return a TestErrorEvent representing an error that occurred during  
         *         execution, or <code>null</code> if no such error occurs. 
         */
        public TestErrorEvent execute() throws GDException;
        
    }
    
    /**
     * abstract class for shared methods
     *
     * @author BREDEX GmbH
     * @created 19.08.2009
     */
    public abstract class AbstractPostExecutionCommand 
        implements IPostExecutionCommand {
        
        /** Constructor */
        private AbstractPostExecutionCommand() {
        // empty
        }
        
        /**
         * Method to find the value for a parameter
         * 
         * @param date test data object
         * @param cap the corresponding cap
         * @param desc param description belonging to current test data object
         * @return the value
         * @throws InvalidDataException
         *      in case of missing value for a parameter in a cap
         */
        protected String getValueForParam(ITestDataPO date, ICapPO cap, 
            IParamDescriptionPO desc) 
            throws InvalidDataException {
            String value = StringConstants.EMPTY;
            ParamValueConverter conv = new ModelParamValueConverter(
                date.getValue().getValue(getLocale()), cap, getLocale(), desc);
            try {
                List <ExecObject> stackList = 
                    new ArrayList<ExecObject>(m_trav.getExecStackAsList());
                value = conv.getExecutionString(stackList, getLocale());
            } catch (InvalidDataException e) {
                throw new InvalidDataException("Neither value nor reference for Node: "  //$NON-NLS-1$
                    + cap.getName(), MessageIDs.E_NO_REFERENCE); 
            }
            return value;
        }
        
        /** 
         * @param paramID the parameter id 
         * @return the value of the current paramID parameter */
        protected String getValueForParam(String paramID) throws GDException {
            IParamDescriptionPO desc = m_currentCap
                    .getParameterForUniqueId(paramID);
            ITDManagerPO tdManager = 
                m_externalTestDataBP.getExternalCheckedTDManager(m_currentCap);
            ITestDataPO date = tdManager.getCell(0, desc);
            return this.getValueForParam(date, m_currentCap, desc);
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
}