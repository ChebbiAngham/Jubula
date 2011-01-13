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
package org.eclipse.jubula.client.core.model;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jubula.client.core.ClientTestFactory;
import org.eclipse.jubula.client.core.businessprocess.TestExecution;
import org.eclipse.jubula.tools.constants.AutConfigConstants;
import org.eclipse.jubula.tools.objects.IMonitoringValue;


/**
 * Test Result implementation that provides data based on the current state of
 * Test Execution.
 *
 * @author BREDEX GmbH
 * @created Aug 6, 2010
 */
public class TestResult extends AbstractTestResult {
   
    /** the name of the project within which the test was executed */
    private String m_projectName;

    /** the GUID of the project within which the test was executed */
    private String m_projectGuid;
    
    /** 
     * the major version number of the project within which the test was 
     * executed 
     */
    private int m_projectMajorVersionNumber;

    /** 
     * the minor version number of the project within which the test was 
     * executed 
     */
    private int m_projectMinorVersionNumber;

    /** the database ID of the project within which the test was executed */
    private long m_projectId;
    
    /** the proifling agent id*/
    private String m_monitoringId;
    /** the calculated monitoring values*/
    private Map<String, IMonitoringValue> m_monitoringValues;
    /** the monitoring report blob as byte array */
    private byte[] m_reportData;   
    /** is monitoring report written? */
    private boolean m_reportWritten;
    /**
     * Constructor
     * 
     * @param project The project within which the test was executed. Must
     *                not be <code>null</code>.
     * @param rootResultNode The root of the Test Result tree. Must not be 
     *                       <code>null</code>.
     */
    public TestResult(IProjectPO project, TestResultNode rootResultNode) {
        super(rootResultNode);
        m_projectName = project.getName();
        m_projectGuid = project.getGuid();
        m_projectMajorVersionNumber = project.getMajorProjectVersion();
        m_projectMinorVersionNumber = project.getMinorProjectVersion();
        m_projectId = project.getId();
    }

    /**
     * 
     * @return the most recently started AUT Configuration. 
     */
    private IAUTConfigPO getAutConfig() {
        return ClientTestFactory.getClientTest().getLastAutConfig();
    }
    
    /**
     * {@inheritDoc}
     */
    public String getAutAgentHostName() {
        IAUTConfigPO autConfig = getAutConfig();
        return autConfig != null ? autConfig.getServer() : StringUtils.EMPTY;
    }

    /**
     * {@inheritDoc}
     */
    public String getAutArguments() {
        IAUTConfigPO autConfig = getAutConfig();
        return autConfig != null 
            ? autConfig.getValue(AutConfigConstants.AUT_ARGUMENTS, 
                    StringUtils.EMPTY) : StringUtils.EMPTY;
    }

    /**
     * {@inheritDoc}
     */
    public String getAutConfigName() {
        IAUTConfigPO autConfig = getAutConfig();
        return autConfig != null ? autConfig.getName() : StringUtils.EMPTY;
    }

    /**
     * {@inheritDoc}
     */
    public Date getEndTime() {
        return ClientTestFactory.getClientTest().getEndTime();
    }

    /**
     * {@inheritDoc}
     */
    public int getExpectedNumberOfSteps() {
        return TestExecution.getInstance().getExpectedNumberOfSteps();
    }

    /**
     * {@inheritDoc}
     */
    public int getNumberOfEventHandlerSteps() {
        return TestExecution.getInstance().getNumberOfEventHandlerSteps()
            + TestExecution.getInstance().getNumberOfRetriedSteps();
    }

    /**
     * {@inheritDoc}
     */
    public int getNumberOfFailedSteps() {
        return TestExecution.getInstance().getNumberOfFailedSteps();
    }

    /**
     * {@inheritDoc}
     */
    public int getNumberOfTestedSteps() {
        return TestExecution.getInstance().getNumberOfTestedSteps();
    }

    /**
     * {@inheritDoc}
     */
    public String getProjectGuid() {
        return m_projectGuid;
    }

    /**
     * {@inheritDoc}
     */
    public long getProjectId() {
        return m_projectId;
    }

    /**
     * {@inheritDoc}
     */
    public int getProjectMajorVersion() {
        return m_projectMajorVersionNumber;
    }

    /**
     * {@inheritDoc}
     */
    public int getProjectMinorVersion() {
        return m_projectMinorVersionNumber;
    }

    /**
     * {@inheritDoc}
     */
    public String getProjectName() {
        return m_projectName;
    }

    /**
     * {@inheritDoc}
     */
    public Date getStartTime() {
        return ClientTestFactory.getClientTest().getTestsuiteStartTime();
    }

    /**
     * {@inheritDoc}
     */
    public String getTestLanguage() {
        Locale testLocale = TestExecution.getInstance().getLocale();
        return testLocale != null 
            ? testLocale.getDisplayName() : StringUtils.EMPTY;
    }

    /**
     * @return the monitoringId
     */
    public String getMonitoringId() {
        return m_monitoringId;
    }
    /**
     * 
     * @return The monitored values
     */
    public Map<String, IMonitoringValue> getMonitoringValues() {
        return m_monitoringValues;
    }
    /**
     * 
     * @param monitoringValues the MonitoringValues to set
     */
    public void setMonitoringValues(
            Map<String, IMonitoringValue> monitoringValues) {
        this.m_monitoringValues = monitoringValues;
    }

    /**
     * @param monitoringId the monitoringId to set
     */
    public void setMonitoringId(String monitoringId) {
        m_monitoringId = monitoringId;
    } 

    /**
     * @return the reportData
     */
    public byte[] getReportData() {
        return m_reportData;
    }

    /**
     * @param reportData the reportData to set
     */
    public void setReportData(byte[] reportData) {
        m_reportData = reportData;
    }
    /**
     * 
     * @return true if report was written, false otherwise.
     */
    public boolean isReportWritten() {
        return m_reportWritten;
    }
    /**
     * 
     * @param reportWritten set reportWritten
     */
    public void setReportWritten(boolean reportWritten) {
        this.m_reportWritten = reportWritten;
    }
       
    
}
