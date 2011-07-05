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
package org.eclipse.jubula.client.core.persistence;

import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;

import org.apache.commons.lang.time.DateUtils;
import org.eclipse.jubula.client.core.events.DataEventDispatcher;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.TestresultState;
import org.eclipse.jubula.client.core.i18n.Messages;
import org.eclipse.jubula.client.core.model.ITestResultPO;
import org.eclipse.jubula.client.core.model.PoMaker;
import org.eclipse.jubula.tools.exception.JBException;
import org.eclipse.jubula.tools.exception.JBFatalException;
import org.eclipse.jubula.tools.exception.ProjectDeletedException;
import org.eclipse.jubula.tools.messagehandling.MessageIDs;


/**
 * PM to handle all test result related Persistence (JPA / EclipseLink) queries
 * 
 * @author BREDEX GmbH
 * @created Mar 3, 2010
 */
public class TestResultPM {
    
    /**
     * hide
     */
    private TestResultPM() {
    // empty
    }

    /**
     * store test result details of test result node in database
     * @param session Session
     */
    public static final void storeTestResult(EntityManager session) {
        try {            
            final EntityTransaction tx = 
                Persistor.instance().getTransaction(session);
            Persistor.instance().commitTransaction(session, tx);
        } catch (PMException e) {
            throw new JBFatalException(Messages.StoringOfTestResultsFailed, e,
                    MessageIDs.E_DATABASE_GENERAL);
        } catch (ProjectDeletedException e) {
            throw new JBFatalException(Messages.StoringOfTestResultsFailed, e,
                    MessageIDs.E_PROJECT_NOT_FOUND);
        } finally {
            Persistor.instance().dropSession(session);
        }
    }
    
    /**
     * delete test result elements of selected summary
     * @param resultId id of test result
     */
    private static final void deleteTestresultOfSummary(
            Long resultId) {
        
        Persistor persistor = Persistor.instance();
        if (persistor == null) {
            return;
        }
        final EntityManager session = persistor.openSession();
        try {
            final EntityTransaction tx = 
                persistor.getTransaction(session);
            persistor.lockDB();
            executeDeleteTestresultOfSummary(session, resultId);
            deleteMonitoringReports(session, resultId);
            
            persistor.commitTransaction(session, tx);
        } catch (PMException e) {
            throw new JBFatalException(Messages.DeleteTestresultElementFailed, 
                    e, MessageIDs.E_DATABASE_GENERAL);
        } catch (ProjectDeletedException e) {
            throw new JBFatalException(Messages.DeleteTestresultElementFailed, 
                    e, MessageIDs.E_PROJECT_NOT_FOUND);
        } finally {
            persistor.dropSession(session);
            persistor.unlockDB();
        }
    }
    
    /**
     * clean monitoring reports by age (days of existence)
     * @param session The current session
     * @param summaryId The summaryToDelete
     */
    private static void deleteMonitoringReports(
            EntityManager session, Long summaryId) {

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("update ") //$NON-NLS-1$
            .append(PoMaker.getTestResultSummaryClass().getSimpleName())
            .append(" summary set summary.report = :report, summary.reportWritten = :isReportWritten"); //$NON-NLS-1$
        if (summaryId != null) {
            queryBuilder.append(" where summary.id = :id"); //$NON-NLS-1$
        }

        Query q = session.createQuery(queryBuilder.toString());
        q.setParameter("report", null); //$NON-NLS-1$
        q.setParameter("isReportWritten", false); //$NON-NLS-1$
        if (summaryId != null) {
            q.setParameter("id", summaryId); //$NON-NLS-1$
        }
        
        q.executeUpdate();

    }    
    
    
    /**
     * execute delete-test-result of summary without commit
     * @param session Session
     * @param resultId id of testresult-summary-entry, or <code>null</code> if 
     *                 all test results should be deleted.
     */
    public static final void executeDeleteTestresultOfSummary(
            EntityManager session, Long resultId) {
        boolean isDeleteAll = resultId == null;
        
        //delete parameter details of test results
        String paramQueryBaseString = 
            "delete from PARAMETER_DETAILS"; //$NON-NLS-1$
        if (isDeleteAll) {
            session.createNativeQuery(paramQueryBaseString).executeUpdate();
        } else {
            Query paramQuery = session.createNativeQuery(
                    paramQueryBaseString + " where FK_TESTRESULT in (select ID from TESTRESULT where INTERNAL_TESTRUN_ID = #summaryId)"); //$NON-NLS-1$
            paramQuery.setParameter("summaryId", resultId); //$NON-NLS-1$
            paramQuery.executeUpdate();
        }

        //delete test result details
        StringBuilder resultQueryBuilder = new StringBuilder();
        resultQueryBuilder.append(
                "delete from TestResultPO testResult"); //$NON-NLS-1$
        if (!isDeleteAll) {
            resultQueryBuilder.append(" where testResult.internalTestResultSummaryID = :id"); //$NON-NLS-1$
        }
        Query resultQuery = session.createQuery(resultQueryBuilder.toString());
        if (!isDeleteAll) {
            resultQuery.setParameter("id", resultId); //$NON-NLS-1$
        }
        resultQuery.executeUpdate();

    }
    
    /**
     * delete all test result details
     */
    public static final void deleteAllTestresultDetails() {
        deleteTestresultOfSummary(null);
    }
    

    /**
     * clean test result details by age (days of existence)
     * testrun summaries will not be deleted
     * @param days days
     * @param projGUID the project guid
     * @param majorVersion the project major version number
     * @param minorVersion the project minor version number
     */
    public static final void cleanTestresultDetails(int days, String projGUID,
        int majorVersion, int minorVersion) {
        Date cleanDate = DateUtils.addDays(new Date(), days * -1);
        try {
            Set<Long> summaries = TestResultSummaryPM
                    .findTestResultSummariesByDate(cleanDate, projGUID,
                            majorVersion, minorVersion);
            for (Long summaryId : summaries) {
                deleteTestresultOfSummary(summaryId);
            }
            DataEventDispatcher.getInstance().fireTestresultChanged(
                    TestresultState.Refresh);
        } catch (JBException e) {
            throw new JBFatalException(Messages.DeletingTestresultsFailed, e,
                    MessageIDs.E_DELETE_TESTRESULT);
        } 
    }
    
    /**
     * @param session The session in which to execute the Persistence (JPA / EclipseLink) query.
     * @param summaryId The database ID of the summary for which to compute the
     *                  corresponding Test Result nodes.
     * @return the Test Result nodes associated with the given Test Result 
     *         Summary, sorted by sequence (ascending).
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<ITestResultPO> computeTestResultListForSummary(
            EntityManager session, Long summaryId) {

        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery query = builder.createQuery();
        Root from = query.from(PoMaker.getTestResultClass());
        query.orderBy(builder.asc(from.get("keywordSequence"))) //$NON-NLS-1$
            .select(from).where(
                builder.equal(from.get("internalTestResultSummaryID"), summaryId)); //$NON-NLS-1$
        
        return session.createQuery(query).getResultList();
    }
    
    /**
     * @param session The session in which to execute the Persistence (JPA / EclipseLink) query.
     * @param summaryId The database ID of the summary for which to compute the
     *                  corresponding Test Result nodes.
     * @return the Test Result nodes associated with the given Test Result 
     *         Summary, sorted by sequence (ascending).
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static boolean hasTestResultDetails(
            EntityManager session, Long summaryId) {
        boolean hasDetails = false;
        
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery query = builder.createQuery();
        Root from = query.from(PoMaker.getTestResultClass());
        query.select(builder.count(from)).where(
            builder.equal(from.get("internalTestResultSummaryID"), summaryId)); //$NON-NLS-1$
        
        Number result = (Number)session.createQuery(query).getSingleResult();
        if (result.longValue() > 0) {
            hasDetails = true;
        }
        return hasDetails;
    }
    
    /**
     * @param session
     *            The session in which to execute the Persistence (JPA / EclipseLink) query.
     * @return a list of test result ids that have test result details
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<Number> 
    computeTestresultIdsWithDetails(EntityManager session) {

        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery query = builder.createQuery();
        Path from = query.from(PoMaker.getTestResultClass()).get("internalTestResultSummaryID"); //$NON-NLS-1$
        query.select(from).distinct(true);
        
        return session.createQuery(query).getResultList();
    }
}
