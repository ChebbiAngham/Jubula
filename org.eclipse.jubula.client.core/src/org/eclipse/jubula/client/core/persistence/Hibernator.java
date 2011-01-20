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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jubula.client.core.ClientTestFactory;
import org.eclipse.jubula.client.core.constants.PluginConstants;
import org.eclipse.jubula.client.core.errorhandling.IDatabaseVersionErrorHandler;
import org.eclipse.jubula.client.core.i18n.Messages;
import org.eclipse.jubula.client.core.model.DBVersionPO;
import org.eclipse.jubula.client.core.model.IPersistentObject;
import org.eclipse.jubula.client.core.persistence.locking.LockManager;
import org.eclipse.jubula.client.core.utils.DBSchemaPropertyCreator;
import org.eclipse.jubula.client.core.utils.ProgressEvent;
import org.eclipse.jubula.client.core.utils.ProgressEventDispatcher;
import org.eclipse.jubula.tools.constants.StringConstants;
import org.eclipse.jubula.tools.exception.Assert;
import org.eclipse.jubula.tools.exception.JBException;
import org.eclipse.jubula.tools.exception.JBFatalAbortException;
import org.eclipse.jubula.tools.exception.JBFatalException;
import org.eclipse.jubula.tools.exception.ProjectDeletedException;
import org.eclipse.jubula.tools.jarutils.IVersion;
import org.eclipse.jubula.tools.messagehandling.MessageIDs;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.eclipse.persistence.exceptions.DatabaseException;
import org.eclipse.persistence.jpa.osgi.PersistenceProvider;
import org.eclipse.persistence.sessions.server.ServerSession;
import org.eclipse.persistence.tools.schemaframework.SchemaManager;

/**
 * @author BREDEX GmbH
 * @created 19.04.2005
 */

public class Hibernator {
    /** the name of the default persistence unit for Jubula */
    private static final String DEFAULT_PU_NAME = "org.eclipse.jubula"; //$NON-NLS-1$
    
    /** shutdown hook to dispose the current Hibernator */
    private static final Thread SHUTDOWN_HOOK = new Thread(
            "Close Session Factory") { //$NON-NLS-1$
        public void run() {
            Hibernator hib = Hibernator.instance();
            if (hib != null) {
                try {
                    if (LockManager.isRunning()) {
                        LockManager.instance().dispose();
                    }
                } catch (Throwable t) {
                    log.warn(Messages.CouldNotShutDownLockManager 
                            + StringConstants.DOT, t);
                }
                try {
                    hib.dispose();
                } catch (Throwable t) {
                    log.warn(Messages.CouldNotShutDownDatabaseConnectionPool 
                            + StringConstants.DOT, t);
                }
            }
        }
    };

    static {
        Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
    }

    /** standard logging */
    private static Log log = LogFactory.getLog(Hibernator.class);

    /** singleton */
    private static Hibernator instance = null;

    /** username */
    private static String user = null;

    /** password */
    private static String pw = null;

    /** db connection string */
    private static String dburl = null;

    /** schema name */
    private static String schemaName = null;

    /** map of schemas */
    private static Map<String, Properties> schemaMap = null;

    /** scheme properties */
    private static Properties schemeProp = null;

    /** is Select DB action */
    private static boolean isSelectDbAction = false;

    /** is headless */
    private static boolean headless = false;

    /** Hibernate configuration */
    private EntityManagerFactory m_sf;

    /** the configuration */
    private Properties m_cfg;

    /** list of known open sessions */
    private List<EntityManager> m_sessions = new ArrayList<EntityManager>();

    /** set to true if a new scheme was installed */
    private boolean m_newDbSchemeInstalled = false;

    /**
     * Create the instance.
     * 
     * @param userName
     *            The user name.
     * @param pwd
     *            The password.
     * @param url
     *            The password.
     * @throws JBException
     *             in case of configuration problems.
     */
    private Hibernator(String userName, String pwd, String url)
        throws JBException, DatabaseVersionConflictException {

        super();
        try {
            if (!isSelectDbAction) {
                ProgressEventDispatcher.notifyListener(new ProgressEvent(
                        ProgressEvent.OPEN_PROGRESS_BAR, null, 
                            Messages.PluginConnectProgress));
            }

            m_cfg = new Properties();
            // add schema properties
            if (schemeProp != null) {
                m_cfg.putAll(schemeProp);
            } else {
                throw new IllegalArgumentException(
                        Messages.ExecutionControllerInvalidDBschemeError, null);
            }

            buildSessionFactoryWithLoginData(userName, pwd, url);
        } catch (PersistenceException e) {
            String msg = Messages.CantSetupHibernate;
            log.fatal(msg, e);
            throw new JBFatalException(msg, MessageIDs.E_HIBERNATE_CANT_SETUP);
        } catch (DatabaseVersionConflictException dbvce) {
            dispose();
            throw dbvce;
        }
    }

    /**
     * Inits the hibernator.
     * 
     * @return boolean True, if Hibernator could initialized. False, otherwise.
     * @throws JBFatalException
     *             in case of setup problems.
     */
    public static synchronized boolean init() throws JBFatalException {
        if (instance == null) {
            loadSchemaProperties();
            if (!isSelectDbAction) {
                if (schemaMap.size() == 1) {
                    connectWithoutDialog();
                } else {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.LOGIN, null, null));
                }
            }
            schemeProp = schemaMap.get(schemaName);
        } else {
            return true;
        }
        while (user != null) {
            try {
                instance(user, pw, dburl);
                user = null;
                pw = null;
                if (instance.m_newDbSchemeInstalled) {
                    instance.m_newDbSchemeInstalled = false;
                    instance.importUnboundModules();
                }
                fireTestresultSummaryChanged();
                return true;
            } catch (PMDatabaseConfException e) {
                if (e.getErrorId().equals(MessageIDs.E_INVALID_DB_VERSION)) {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.SHOW_MESSAGE,
                            MessageIDs.E_INVALID_DB_VERSION, null));
                } else if (e.getErrorId().equals(
                        MessageIDs.E_NOT_CHECKABLE_DB_VERSION)) {
                    ProgressEventDispatcher
                            .notifyListener(new ProgressEvent(
                                    ProgressEvent.SHOW_MESSAGE,
                                    MessageIDs.E_NOT_CHECKABLE_DB_VERSION,
                                    null));
                } else if (e.getErrorId().equals(MessageIDs.E_NO_DB_SCHEME)) {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.SHOW_MESSAGE,
                            MessageIDs.E_NO_DB_SCHEME, null));
                } else if (e.getErrorId().equals(
                        MessageIDs.E_ERROR_IN_SCHEMA_CONFIG)) {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.SHOW_MESSAGE,
                            MessageIDs.E_ERROR_IN_SCHEMA_CONFIG, null));

                } else {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.SHOW_MESSAGE,
                            MessageIDs.E_UNEXPECTED_EXCEPTION, null));
                }
                return false;
            } catch (JBException e) {
                ProgressEventDispatcher.notifyListener(new ProgressEvent(
                        ProgressEvent.CLOSE_PROGRESS_BAR, null, null));
                if (e.getErrorId().equals(MessageIDs.E_NO_DB_CONNECTION)) {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.SHOW_MESSAGE,
                            MessageIDs.E_NO_DB_CONNECTION, null));
                }
                if (e.getErrorId().equals(MessageIDs.E_DB_IN_USE)) {
                    ProgressEventDispatcher.notifyListener(new ProgressEvent(
                            ProgressEvent.SHOW_MESSAGE, MessageIDs.E_DB_IN_USE,
                            null));
                }
                ProgressEventDispatcher.notifyListener(new ProgressEvent(
                        ProgressEvent.LOGIN, null, null));
            }
        }
        return false;
    }

    /**
     * fire test result summary changed event, when new connected to database
     */
    private static void fireTestresultSummaryChanged() {
        if (!headless) {
            ClientTestFactory.getClientTest().fireTestresultSummaryChanged();
        }
    }

    /**
     * if only one scheme with user and password is defined, don't show login
     * dialog
     */
    private static void connectWithoutDialog() {
        Collection<Properties> values = schemaMap.values();
        Object[] keys = schemaMap.keySet().toArray();
        Iterator<Properties> it = values.iterator();
        if (it.hasNext()) {
            schemeProp = it.next();
            String predefinedUsername = schemeProp
                    .getProperty(PersistenceUnitProperties.JDBC_USER);
            String predefinedPassword = schemeProp
                    .getProperty(PersistenceUnitProperties.JDBC_PASSWORD);
            String schemName = keys[0].toString();
            if (predefinedUsername != null && predefinedPassword != null) {
                user = predefinedUsername;
                pw = predefinedPassword;
                schemaName = schemName;
            } else {
                ProgressEventDispatcher.notifyListener(new ProgressEvent(
                        ProgressEvent.LOGIN, null, null));
            }
        }

    }

    /**
     * Loads the schema properties file.
     */
    private static void loadSchemaProperties() throws JBFatalException {
        schemaMap = DBSchemaPropertyCreator.getSchemaMap();
    }

    /**
     * <p>
     * <FONT COLOR="#FF0000">ATTENTION:</FONT><b><i> Call this method, only when
     * the database connection failed because of invalid login data and you want
     * to login again with valid login data !</i></b>
     * </p>
     * 
     * @param userName
     *            The user name.
     * @param pwd
     *            The password.
     * @param url
     *            url connection string
     * @throws PersistenceException
     *             in case of configuration problems.
     * @throws JBException
     *             in case of configuration problems.
     * @throws PMDatabaseConfException
     *             in case of invalid db scheme
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void buildSessionFactoryWithLoginData(String userName, String pwd,
            String url) throws PersistenceException, PMDatabaseConfException,
            JBException, DatabaseVersionConflictException {

        if (m_cfg != null) {
            m_cfg.setProperty(PersistenceUnitProperties.JDBC_USER, userName);
            m_cfg.setProperty(PersistenceUnitProperties.JDBC_PASSWORD, pwd);
            if (url != null) {
                m_cfg.setProperty(PersistenceUnitProperties.JDBC_URL, url);
            }

            // use the classloader for this bundle when initializing 
            // the EntityManagerFactory
            Map properties = new HashMap(m_cfg);
            properties.put(PersistenceUnitProperties.CLASSLOADER, 
                    this.getClass().getClassLoader());
            m_sf = new PersistenceProvider().createEntityManagerFactory(
                    DEFAULT_PU_NAME, 
                    properties);

            EntityManager em = null;
            try {
                em = m_sf.createEntityManager();
                try {
                    validateDBVersion(em);

                } catch (AmbiguousDatabaseVersionException e) {
                    throw new PMDatabaseConfException(
                            Messages.DBVersionProblem + StringConstants.DOT, 
                            MessageIDs.E_NOT_CHECKABLE_DB_VERSION);
                }
            } catch (PersistenceException e) {
                Throwable rootCause = ExceptionUtils.getRootCause(e);
                if (rootCause instanceof SQLException) {
                    if (("08001").equals(((SQLException)rootCause).getSQLState())) { //$NON-NLS-1$
                        log.error(
                            Messages.TheDatabaseIsAlreadyInUseByAnotherProcess, 
                                e);
                        throw new JBException(e.getMessage(),
                                MessageIDs.E_DB_IN_USE);
                    }
                    log.error(Messages.NoOrWrongUsernameOrPassword, e);
                    throw new JBException(e.getMessage(),
                            MessageIDs.E_NO_DB_CONNECTION);
                }
            } finally {
                if (em != null) {
                    em.close();
                }
            }
        } else {
            Assert.verify(false,
                    Messages.TheSessionConfigurationWasNotInitialized);
        }
    }

    /**
     * Queries registered error handlers in an effort to resolve a database
     * version conflict.
     * 
     * @return <code>true</code> if the conflict has been resolved as a result
     *         of this method call. Otherwise, <code>false</code>.
     */
    private static boolean handleDatabaseVersionConflict() {
        List<IDatabaseVersionErrorHandler> errorHandlers = 
            new ArrayList<IDatabaseVersionErrorHandler>();
        IConfigurationElement[] config = Platform.getExtensionRegistry()
                .getConfigurationElementsFor(
                        PluginConstants.DB_VERSION_HANDLER_EXT_ID);
        for (IConfigurationElement e : config) {
            try {
                final Object o = e
                        .createExecutableExtension(
                            PluginConstants.DB_VERSION_HANDLER_CLASS_ATTR);
                if (o instanceof IDatabaseVersionErrorHandler) {
                    errorHandlers.add((IDatabaseVersionErrorHandler) o);
                }
            } catch (CoreException ce) {
                log.warn(
                        Messages.ErrorOccurredInitializingDatabaseVersion
                        + StringConstants.DOT, ce);
            }
        }

        if (errorHandlers.isEmpty()) {
            return false;
        }
        for (final IDatabaseVersionErrorHandler handler : errorHandlers) {
            final AtomicBoolean isHandled = new AtomicBoolean(false);
            ISafeRunnable runnable = new ISafeRunnable() {
                public void handleException(Throwable t) {
                    log.warn(
                        Messages.ErrorOccurredResolvingDatabaseVersionConflict
                        + StringConstants.DOT, t);
                }

                public void run() throws Exception {
                    isHandled.set(handler.handleDatabaseError());
                }
            };
            SafeRunner.run(runnable);
            if (isHandled.get()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validates the consistency of DbVersion and Jubula Client Version
     * 
     * @param session
     *            current session
     * @throws PMDatabaseConfException
     *             in case of any problem with db scheme
     * @throws JBException
     *             in case of a configuration problem
     * @throws DatabaseVersionConflictException
     * @throws AmbiguousDatabaseVersionException
     */
    @SuppressWarnings("unchecked")
    private void validateDBVersion(EntityManager session)
        throws PMDatabaseConfException, JBException,
        DatabaseVersionConflictException, AmbiguousDatabaseVersionException {
        List<DBVersionPO> hits = null;
        try {
            hits = session.createQuery("select version from DBVersionPO as version").getResultList(); //$NON-NLS-1$
        } catch (RuntimeException e) {
            // FIXME zeb We were catching a PersistenceException here, but that
            //           does not work for EclipseLink's JPA because they throw
            //           a DatabaseException if something goes wrong with the
            //           database (ex. missing table). We shouldn't have to 
            //           worry about catching vendor-specific exceptions, but 
            //           it looks like we do. See:
            //           http://stackoverflow.com/questions/2394885/what-exceptions-are-thrown-by-jpa-in-ejb-containers
            //           for a brief discussion on the topic.
            Throwable cause = ExceptionUtils.getCause(e);
            if (cause instanceof SQLException) {
                SQLException se = (SQLException)cause;
                if (se.getErrorCode() == 17002) {
                    final String msg = Messages.ProblemWithDatabaseSchemeConf
                        + StringConstants.DOT;
                    log.fatal(msg);
                    throw new PMDatabaseConfException(msg,
                            MessageIDs.E_ERROR_IN_SCHEMA_CONFIG);
                }
            }
            m_newDbSchemeInstalled = 
                installDbScheme(session.getEntityManagerFactory());
            try {
                hits = session.createQuery("select version from DBVersionPO as version").getResultList(); //$NON-NLS-1$
            } catch (PersistenceException pe) {
                final String msg = Messages.ProblemWithInstallingDBScheme 
                    + StringConstants.DOT;
                log.fatal(msg);
                throw new PMDatabaseConfException(msg,
                        MessageIDs.E_NO_DB_SCHEME);
            } catch (DatabaseException dbe) {
                // FIXME zeb EclipseLink's JPA throws a DatabaseException if 
                //           something goes wrong with the database 
                //           (ex. missing table), instead of wrapping it in a 
                //           PersistenceException. We shouldn't have to 
                //           worry about catching vendor-specific exceptions, but 
                //           it looks like we do. See:
                //           http://stackoverflow.com/questions/2394885/what-exceptions-are-thrown-by-jpa-in-ejb-containers
                //           for a brief discussion on the topic.
                final String msg = Messages.ProblemWithInstallingDBScheme 
                    + StringConstants.DOT;
                log.fatal(msg);
                throw new PMDatabaseConfException(msg,
                        MessageIDs.E_NO_DB_SCHEME);
            }
        }
        if (!hits.isEmpty() && hits.size() == 1) {
            DBVersionPO dbVersion = hits.get(0);
            Integer dbMaj = dbVersion.getMajorVersion();
            Integer dbMin = dbVersion.getMinorVersion();
            if (dbMaj.equals(IVersion.JB_DB_MAJOR_VERSION)) {
                if (dbMin.equals(IVersion.GD_DB_MINOR_VERSION)) {
                    log.info(Messages.DBVersion + StringConstants.COLON
                            + StringConstants.SPACE + Messages.OK);
                } else {
                    log.error(Messages.DBVersion + StringConstants.COLON
                            + StringConstants.SPACE
                            + Messages.MinorVersionInvalid);
                    throw new DatabaseVersionConflictException(dbMaj, dbMin);
                }
            } else {
                log.fatal(Messages.DBVersion + StringConstants.COLON
                        + StringConstants.SPACE + Messages.MajorVersionInvalid);
                throw new DatabaseVersionConflictException(dbMaj, dbMin);
            }
        } else {
            log.error(Messages.DBVersion + StringConstants.COLON
                    + StringConstants.SPACE + Messages.DBEntryMissingAmbiguous);
            throw new AmbiguousDatabaseVersionException(hits);
        }
    }

    /**
     * Installs the DB scheme used by Jubula.
     * 
     * @param entityManagerFactory
     *            The factory to use to create the entity manager in which the
     *            installation will occur.
     * 
     * @return <code>true</code> if the installation was successful. Otherwise,
     *         <code>false</code>.
     * @throws PMDatabaseConfException
     *             if the scheme couldn't be installed
     * @throws JBException
     *             in case of configuration problems
     */
    private static boolean installDbScheme(
            EntityManagerFactory entityManagerFactory) 
        throws PMDatabaseConfException, JBException {

        EntityManager em = null;
        try {
            em = entityManagerFactory.createEntityManager();
            SchemaManager schemaManager = 
                new SchemaManager(em.unwrap(ServerSession.class));
            schemaManager.replaceDefaultTables();
            
            createOrUpdateDBVersion(em);
            createOrUpdateDBGuard(em);
            return true;
        } catch (PersistenceException e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof SQLException) {
                if (("08001").equals(((SQLException)rootCause).getSQLState())) { //$NON-NLS-1$
                    log.error(Messages.TheDBAllreadyUseAnotherProcess, e);
                    throw new JBException(rootCause.getMessage(), 
                            MessageIDs.E_DB_IN_USE);
                }
                log.error(Messages.NoOrWrongUsernameOrPassword, e);
                throw new JBException(e.getMessage(), 
                    MessageIDs.E_NO_DB_CONNECTION);
            }
            final String msg = Messages.ProblemInstallingDBScheme
                + StringConstants.DOT;
            log.fatal(msg);
            throw new PMDatabaseConfException(msg, MessageIDs.E_NO_DB_SCHEME);
        } finally {
            if (em != null) {
                try {
                    em.close();
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
    }

    /**
     * import all unbound modules into the DB
     */
    private void importUnboundModules() {
        ProgressEventDispatcher.notifyListener(new ProgressEvent(
                ProgressEvent.DB_SCHEME_CREATE, null, null));
    }

    /**
     * 
     * @param em
     *            entity manager
     * @throws PersistenceException
     *             if we blow up
     */
    private static void createOrUpdateDBVersion(EntityManager em)
        throws PersistenceException {
        EntityTransaction tx = null;
        try {
            tx = em.getTransaction();
            tx.begin();

            try {
                DBVersionPO version = 
                    (DBVersionPO)em.createQuery("select version from DBVersionPO as version").getSingleResult(); //$NON-NLS-1$
                version.setMajorVersion(IVersion.JB_DB_MAJOR_VERSION);
                version.setMinorVersion(IVersion.GD_DB_MINOR_VERSION);
                em.merge(version);
            } catch (NoResultException nre) {
                em.merge(new DBVersionPO(IVersion.JB_DB_MAJOR_VERSION,
                        IVersion.GD_DB_MINOR_VERSION));
            }

            tx.commit();
        } catch (PersistenceException pe) {
            if (tx != null) {
                tx.rollback();
            }
            throw pe;
        }
    }

    /**
     * 
     * @param em
     *            entity manager
     * @throws PersistenceException
     *             if we blow up
     */
    private static void createOrUpdateDBGuard(EntityManager em)
        throws PersistenceException {
        EntityTransaction tx = null;
        try {
            tx = em.getTransaction();
            tx.begin();

            try {
                em.createQuery("select guard from DbGuardPO as guard").getSingleResult(); //$NON-NLS-1$
            } catch (NoResultException nre) {
                LockManager.initDbGuard(em);
            }

            tx.commit();
        } catch (PersistenceException pe) {
            if (tx != null) {
                tx.rollback();
            }
            throw pe;
        }
    }

    /**
     * @return the only instance of the Hibernator
     */
    public static Hibernator instance() {
        return instance;
    }

    /**
     * @param userName
     *            The user name.
     * @param pwd
     *            The password.
     * @param url
     *            connection string / url
     * @return the only instance of the Hibernator, building the connection to
     *         the database
     * @throws JBFatalException .
     * @throws JBException .
     */
    private static Hibernator instance(String userName, String pwd, String url)
        throws JBFatalException, JBException {
        if (instance == null) {
            try {
                instance = new Hibernator(userName, pwd, url);
            } catch (DatabaseVersionConflictException e) {
                if (IVersion.JB_DB_MAJOR_VERSION > e
                        .getDatabaseMajorVersion()
                        || (IVersion.JB_DB_MAJOR_VERSION.equals(e
                                .getDatabaseMajorVersion()) 
                                && 
                                IVersion.GD_DB_MINOR_VERSION > e
                                .getDatabaseMinorVersion())) {
                    // Client is newer than database schema
                    if (!handleDatabaseVersionConflict()) {
                        throw new PMDatabaseConfException(
                                Messages.DBVersionProblem + StringConstants.DOT,
                                    MessageIDs.E_INVALID_DB_VERSION);
                    }
                } else {
                    // Client is older than database schema
                    throw new PMDatabaseConfException(
                            Messages.DBVersionProblem + StringConstants.DOT, 
                                MessageIDs.E_INVALID_DB_VERSION);
                }
            } finally {
                if (!isSelectDbAction) {
                    ProgressEventDispatcher
                            .notifyListener(new ProgressEvent(
                                    ProgressEvent.CLOSE_PROGRESS_BAR, null,
                                    null));
                }
                setSelectDBAction(false);

            }

            LockManager.instance().startKeepAlive();
        }
        return instance;
    }

    /**
     * @return a new Session for the standard configuration
     * @throws JBFatalAbortException
     *             if the open failed
     */
    public EntityManager openSession() throws JBFatalAbortException {
        try {
            // FIXME zeb we used to configure the opened session with an 
            //           interceptor in order to track progress. figure out a 
            //           way to include something like an 
            //           interceptor / entity listener.
            //           Reference: Session s = m_sf.openSession(HbmProgressInterceptor.getInstance());
            EntityManager em = m_sf.createEntityManager();
            m_sessions.add(em);
            return em;
        } catch (PersistenceException e) {
            String msg = Messages.PersistenceErrorCreateEntityManagerFailed;
            log.error(msg, e);
            throw new JBFatalAbortException(msg, e, 
                MessageIDs.E_SESSION_FAILED);
        }
    }
    
    /**
     * @param s
     *            Session which is used for the transaction
     * @param tx
     *            transaction to rollback
     * @throws PMException
     *             in case of failed rollback
     */
    public void rollbackTransaction(EntityManager s, EntityTransaction tx)
        throws PMException {

        Validate.notNull(s);
        if (tx != null) {
            //FIXME tobi NLS ??
            Validate.isTrue(tx.equals(s.getTransaction()),
                    "Session and Transaction don't match"); //$NON-NLS-1$
            try {
                tx.rollback();
            } catch (PersistenceException e) {
                log.error(Messages.RollbackFailed, e);
                if (s.equals(GeneralStorage.getInstance().getMasterSession())) {
                    GeneralStorage.getInstance().recoverSession();
                }
                throw new PMException(Messages.RollbackFailed,
                        MessageIDs.E_DATABASE_GENERAL);
            } finally {
                removeLocks(s);
            }
        }
    }

    /**
     * @param s
     *            Session to flush
     * @throws PMException
     *             in case of failed flush
     */
    public void flushSession(EntityManager s) throws PMException {
        Validate.notNull(s, "No null value allowed"); //$NON-NLS-1$
        try {
            s.flush();
        } catch (PersistenceException e) {
            log.error(Messages.FlushFailed, e);
            if (s.equals(GeneralStorage.getInstance().getMasterSession())) {
                GeneralStorage.getInstance().recoverSession();
            }
            throw new PMException(Messages.FlushFailed,
                    MessageIDs.E_DATABASE_GENERAL);
        } finally {
            removeLocks(s);
        }
    }

    /**
     * Get a transaction for a session. If there is already a transaction
     * active, this transaction will be used. Otherwise a new transaction will
     * be started.
     * 
     * @param s
     *            Session for this transaction
     * @return A already active or a fresh transaction
     */
    public EntityTransaction getTransaction(EntityManager s) {
        EntityTransaction result = s.getTransaction();
        if (result.isActive()) {
            if (log.isDebugEnabled()) {
                log.debug(Messages.JoiningTransaction);
            }
        } else {
            result.begin();
            if (log.isDebugEnabled()) {
                log.debug(Messages.StartingTransaction);
            }
        }
        
        return result;
    }

    /**
     * @param s
     *            session
     * @param tx
     *            transaction
     * @throws PMReadException
     *             {@inheritDoc}
     * @throws PMAlreadyLockedException
     *             {@inheritDoc}
     * @throws PMDirtyVersionException
     *             {@inheritDoc}
     * @throws PMException
     *             {@inheritDoc}
     * @throws ProjectDeletedException
     *             if the project was deleted in another instance
     */
    public void commitTransaction(EntityManager s, EntityTransaction tx)
        throws PMReadException, PMAlreadyLockedException,
        PMDirtyVersionException, PMException, ProjectDeletedException {

        Validate.notNull(s);
        Validate.notNull(tx);
        Validate.isTrue(tx.equals(s.getTransaction()),
                Messages.SessionAndTransactionDontMatch);
        try {
            tx.commit();
        } catch (PersistenceException e) {
            if (s != null
                    && s.equals(GeneralStorage.getInstance().
                        getMasterSession())) {
                PersistenceManager.handleDBExceptionForMasterSession(null, e);
            } else {
                PersistenceManager.handleDBExceptionForAnySession(null, e, s);
            }
        } finally {
            removeLocks(s);
        }
    }

    /**
     * @param s
     *            session to close
     * @param dropLocks
     *            should I drop LockManager locks?
     * @throws PMException
     *             in case of any db error
     */
    private void closeSession(EntityManager s, boolean dropLocks) 
        throws PMException {
        
        Validate.notNull(s);
        try {
            if (s.isOpen()) {
                try {
                    EntityTransaction tx = s.getTransaction();
                    if (tx.isActive()) {
                        rollbackTransaction(s, tx);
                    }
                } finally {
                    s.close();
                }
            }
        } catch (PersistenceException e) {
            log.error(Messages.CloseSessionFailed, e);
        } finally {
            if (dropLocks) {
                removeLocks(s);
            }
            m_sessions.remove(s);
        }
    }

    /**
     * This method performs a closeSession(), but will never report a failure.
     * It's also null-safe.
     * 
     * @param s
     *            the session to close, null allowed
     */
    public void dropSession(EntityManager s) {
        try {
            if (s != null) {
                closeSession(s, true);
            }
        } catch (PMException e) {
            log.error(Messages.CouldntDropSsession, e);
        }
    }

    /**
     * This method performs a closeSession(), but will never report a failure.
     * It's also null-safe. It will not release database locks, i.e. it's
     * supposed to be used by the LockManager itself;
     * 
     * @param s
     *            the session to close, null allowed
     */
    public void dropSessionWithoutLockRelease(EntityManager s) {
        try {
            if (s != null) {
                closeSession(s, false);
            }
        } catch (PMException e) {
            log.error(Messages.CouldntDropSsession, e);
        }

    }

    /**
     * @param s
     *            session which contain object to refresh
     * @param po
     *            object to refresh
     * @param lockMode
     *            lock Mode for refresh operation
     * @throws PMDirtyVersionException
     *             in case of version conflict
     * @throws PMAlreadyLockedException
     *             if object is locked
     * @throws PMException
     *             if rollback failed
     * @throws ProjectDeletedException
     *             if the project was deleted in another instance
     */
    public void refreshPO(EntityManager s, IPersistentObject po, 
            LockModeType lockMode)
        throws PMDirtyVersionException, PMAlreadyLockedException,
        PMException, ProjectDeletedException {

        Validate.notNull(s, Messages.NoNullValueAllowed);
        try {
            s.refresh(po, lockMode);
        } catch (PersistenceException e) {
            PersistenceManager.handleDBExceptionForMasterSession(po, e);
        }
    }

    /**
     * @param s
     *            session for which to remove the list with locked objects
     */
    private void removeLocks(EntityManager s) {
        LockManager.instance().unlockPOs(s);
    }

    /**
     * @param s
     *            session, which contain the current persistent object
     * @param po
     *            object to lock
     * @throws PMAlreadyLockedException
     *             in case of locked object
     * @throws PMDirtyVersionException
     *             if the PO has a different version than its counterpart in the
     *             db
     * @throws PMObjectDeletedException
     *             if the po was deleted by another app
     */
    public void lockPO(EntityManager s, IPersistentObject po)
        throws PMAlreadyLockedException, PMDirtyVersionException,
        PMObjectDeletedException {
        if (!LockManager.instance().lockPO(s, po, true)) {
            String poName = po != null ? po.getName() : StringConstants.EMPTY;
            long poId = po != null ? po.getId() : -1;
            //FIXME tobi NLS ??
            throw new PMAlreadyLockedException(po,
                    "PO " + po + " (name=" + poName + "; id=" + poId + ") locked in db.", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    MessageIDs.E_OBJECT_IN_USE);
        }
    }

    /**
     * tries to lock a set of POs
     * 
     * @param sess
     *            Session
     * @param objectsToLock
     *            Set<IPersistentObject>
     * @throws PMAlreadyLockedException
     *             in case of locked object
     * @throws PMDirtyVersionException
     *             if the PO has a different version than its counterpart in the
     *             db
     * @throws PMObjectDeletedException
     *             if the po was deleted by another app
     */
    public void lockPOSet(EntityManager sess,
            Set<? extends IPersistentObject> objectsToLock)
        throws PMAlreadyLockedException, PMDirtyVersionException,
        PMObjectDeletedException {
        boolean unlock = true;
        try {
            unlock = !LockManager.instance().lockPOs(sess, objectsToLock, true);
        } finally {
            if (unlock) {
                removeLocks(sess);
            }
        }
    }

    /**
     * @param s
     *            session, which contain the current persistent object
     * @param po
     *            object to delete
     * @throws PMException
     *             in case of failed rollback
     * @throws PMAlreadyLockedException
     *             in case of locked object
     * @throws PMDirtyVersionException
     *             in case of version conflict
     * @throws ProjectDeletedException
     *             if the project was deleted in another instance
     * @throws InterruptedException
     *             if the oepration was canceled
     */
    public void deletePO(EntityManager s, IPersistentObject po) 
        throws PMException,
               PMAlreadyLockedException, PMDirtyVersionException,
               ProjectDeletedException, InterruptedException {
        
        Validate.notNull(s);
        try {
            s.remove(po);
        } catch (PersistenceException e) {
            if (e.getCause() instanceof InterruptedException) {
                // Operation was canceled.
                throw new InterruptedException();
            }

            PersistenceManager.handleDBExceptionForMasterSession(po, e);
        }
    }

    /**
     * @param pwd
     *            The pw to set.
     */
    public static void setPw(String pwd) {
        Hibernator.pw = pwd;
    }

    /**
     * @param url
     *            connection strnig
     */
    public static void setUrl(String url) {
        Hibernator.dburl = url;
    }

    /**
     * @param usr
     *            The user to set.
     */
    public static void setUser(String usr) {
        Hibernator.user = usr;
    }

    /**
     * @param schemName
     *            The schema name.
     */
    public static void setSchemaName(String schemName) {
        Hibernator.schemaName = schemName;
    }

    /**
     * set true if client application is running headless (e.g. CmdDbTool)
     * 
     * @param isHeadless
     *            boolean
     */
    public static void setHeadless(boolean isHeadless) {
        Hibernator.headless = isHeadless;
    }

    /**
     * Instanceof variant for hibernate proxies
     * 
     * @param po
     *            PO to be checked
     * @param compClass
     *            is the po a subClass or instance of compClass
     * @return true if po is assignable to compClass
     */
    @SuppressWarnings("unchecked")
    public static boolean isPoSubclass(IPersistentObject po, Class compClass) {
        boolean result = (po == null || compClass == null) ? false : compClass
                .isAssignableFrom(getClass(po));
        return result;
    }

    /**
     * Instanceof variant for hibernate proxies
     * 
     * @param poClass
     *            POclass to be checked
     * @param compClass
     *            is the poClass a subClass or instance of compClass
     * @return true if po is assignable to compClass
     */
    @SuppressWarnings("unchecked")
    public static boolean isPoClassSubclass(Class poClass, Class compClass) {
        boolean result = (poClass == null || compClass == null)
                ? false
                : compClass.isAssignableFrom(poClass);
        return result;
    }

    /**
     * Hibernate.getClass(obj) but null safe
     * 
     * @param obj
     *            the object to get the class for
     * @return the Hibernate.getClass(obj) or null if obj == null
     */
    public static Class getClass(Object obj) {
        return obj == null ? null : HibernateUtil.getClass(obj);
    }

    /**
     * @return current user of DB
     */
    public String getCurrentDBUser() {
        return m_cfg.getProperty(PersistenceUnitProperties.JDBC_USER);
    }

    /**
     * @return current dbuser pw
     */
    public String getCurrentDBPw() {
        return m_cfg.getProperty(PersistenceUnitProperties.JDBC_PASSWORD);
    }

    /**
     * @return current db url
     */
    public String getCurrentDBUrl() {
        return m_cfg.getProperty(PersistenceUnitProperties.JDBC_URL);
    }

    /**
     * @return current schema
     */
    public String getCurrentDBSchema() {
        return m_cfg.getProperty("hibernate.default_schema"); //$NON-NLS-1$
    }

    /**
     * Migrates the structure of the database to match the version of the
     * currently running client. <em>This effectively deletes all Jubula data 
     * currently in the database, so be careful how you use it.</em>
     * 
     * @throws JBFatalException
     *             if a fatal exception occurs during migration.
     * @throws JBException
     *             if a general exception occurs during migration.
     */
    public static void migrateDatabaseStructure() throws JBFatalException,
            JBException {

        boolean wasSelectingDatabase = getSelectDBAction();
        setSelectDBAction(true);
        EntityManagerFactory migrationEntityManagerFactory = null;

        try {
            Properties migrationConfig = new Properties();
            migrationConfig.putAll(schemeProp);
            migrationConfig.setProperty(
                    PersistenceUnitProperties.JDBC_USER, user);
            migrationConfig.setProperty(
                    PersistenceUnitProperties.JDBC_PASSWORD, pw);
            if (dburl != null) {
                migrationConfig.setProperty(
                        PersistenceUnitProperties.JDBC_URL, dburl);
            }

            // use the classloader for this bundle when initializing 
            // the EntityManagerFactory
            Map properties = new HashMap(migrationConfig);
            properties.put(PersistenceUnitProperties.CLASSLOADER, 
                    Hibernator.class.getClassLoader());
            migrationEntityManagerFactory = 
                new PersistenceProvider().createEntityManagerFactory(
                        DEFAULT_PU_NAME, properties);
            installDbScheme(migrationEntityManagerFactory);
            instance = instance(user, pw, dburl);
            instance.m_newDbSchemeInstalled = true;
        } finally {
            setSelectDBAction(wasSelectingDatabase);
            if (migrationEntityManagerFactory != null) {
                migrationEntityManagerFactory.close();
            }
        }
    }

    /**
     * 
     */
    public void dispose() {
        if (m_sf != null) {
            try {
                for (EntityManager sess : m_sessions) {
                    if (sess.isOpen()) {
                        sess.close();
                    }
                }
                m_sessions.clear();
                m_sf.close();
            } catch (Throwable e) {
                log.error(Messages.DisposeOfHibernatorFailed, e);
            }
        }

        instance = null;
    }

    /**
     * true if new DB is selected
     * 
     * @param isSelDbAction
     *            boolean
     */
    public static void setSelectDBAction(boolean isSelDbAction) {
        Hibernator.isSelectDbAction = isSelDbAction;
    }

    /**
     * @return true if new DB is selected
     */
    public static boolean getSelectDBAction() {
        return isSelectDbAction;
    }

    /**
     * @return the JDBC connection class name
     */
    public String getCurrentDBDriverClass() {
        return m_cfg.getProperty(PersistenceUnitProperties.JDBC_DRIVER);
    }
    
}
