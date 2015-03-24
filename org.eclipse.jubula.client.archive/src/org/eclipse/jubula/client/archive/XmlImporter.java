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
package org.eclipse.jubula.client.archive;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.locale.converters.DateLocaleConverter;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jubula.client.archive.converter.AbstractXmlConverter;
import org.eclipse.jubula.client.archive.converter.AutIdGenerationConverter;
import org.eclipse.jubula.client.archive.converter.HTMLTechnicalComponentIndexConverter;
import org.eclipse.jubula.client.archive.converter.IXmlConverter;
import org.eclipse.jubula.client.archive.converter.RefTSNameConverter;
import org.eclipse.jubula.client.archive.converter.TreeDirectionConverter;
import org.eclipse.jubula.client.archive.converter.V4C001;
import org.eclipse.jubula.client.archive.converter.WinToolkitIdConverter;
import org.eclipse.jubula.client.archive.i18n.Messages;
import org.eclipse.jubula.client.schema.Aut;
import org.eclipse.jubula.client.schema.AutConfig;
import org.eclipse.jubula.client.schema.Cap;
import org.eclipse.jubula.client.schema.Category;
import org.eclipse.jubula.client.schema.CheckActivatedContext;
import org.eclipse.jubula.client.schema.CheckAttribute;
import org.eclipse.jubula.client.schema.CheckConfiguration;
import org.eclipse.jubula.client.schema.CompNames;
import org.eclipse.jubula.client.schema.ComponentName;
import org.eclipse.jubula.client.schema.EventHandler;
import org.eclipse.jubula.client.schema.EventTestCase;
import org.eclipse.jubula.client.schema.ExecCategory;
import org.eclipse.jubula.client.schema.I18NString;
import org.eclipse.jubula.client.schema.MapEntry;
import org.eclipse.jubula.client.schema.MonitoringValues;
import org.eclipse.jubula.client.schema.NamedTestData;
import org.eclipse.jubula.client.schema.Node;
import org.eclipse.jubula.client.schema.ObjectMapping;
import org.eclipse.jubula.client.schema.ObjectMappingProfile;
import org.eclipse.jubula.client.schema.OmCategory;
import org.eclipse.jubula.client.schema.OmEntry;
import org.eclipse.jubula.client.schema.ParamDescription;
import org.eclipse.jubula.client.schema.Project;
import org.eclipse.jubula.client.schema.RefTestCase;
import org.eclipse.jubula.client.schema.RefTestSuite;
import org.eclipse.jubula.client.schema.ReportingRule;
import org.eclipse.jubula.client.schema.ReusedProject;
import org.eclipse.jubula.client.schema.SummaryAttribute;
import org.eclipse.jubula.client.schema.TechnicalName;
import org.eclipse.jubula.client.schema.TestCase;
import org.eclipse.jubula.client.schema.TestCase.Teststep;
import org.eclipse.jubula.client.schema.TestData;
import org.eclipse.jubula.client.schema.TestDataCategory;
import org.eclipse.jubula.client.schema.TestDataCell;
import org.eclipse.jubula.client.schema.TestDataRow;
import org.eclipse.jubula.client.schema.TestJobs;
import org.eclipse.jubula.client.schema.TestSuite;
import org.eclipse.jubula.client.schema.TestresultSummaries;
import org.eclipse.jubula.client.schema.TestresultSummary;
import org.eclipse.jubula.client.schema.UsedToolkit;
import org.eclipse.jubula.client.core.businessprocess.ComponentNamesBP.CompNameCreationContext;
import org.eclipse.jubula.client.core.businessprocess.IParamNameMapper;
import org.eclipse.jubula.client.core.businessprocess.IWritableComponentNameCache;
import org.eclipse.jubula.client.core.businessprocess.ProjectNameBP;
import org.eclipse.jubula.client.core.businessprocess.TestDataCubeBP;
import org.eclipse.jubula.client.core.businessprocess.UsedToolkitBP;
import org.eclipse.jubula.client.core.businessprocess.UsedToolkitBP.ToolkitPluginError;
import org.eclipse.jubula.client.core.businessprocess.UsedToolkitBP.ToolkitPluginError.ERROR;
import org.eclipse.jubula.client.core.model.IALMReportingRulePO;
import org.eclipse.jubula.client.core.model.IAUTConfigPO;
import org.eclipse.jubula.client.core.model.IAUTMainPO;
import org.eclipse.jubula.client.core.model.ICapPO;
import org.eclipse.jubula.client.core.model.ICategoryPO;
import org.eclipse.jubula.client.core.model.ICheckConfContPO;
import org.eclipse.jubula.client.core.model.ICheckConfPO;
import org.eclipse.jubula.client.core.model.ICompNamesPairPO;
import org.eclipse.jubula.client.core.model.IComponentNamePO;
import org.eclipse.jubula.client.core.model.IEventExecTestCasePO;
import org.eclipse.jubula.client.core.model.IExecTestCasePO;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IObjectMappingAssoziationPO;
import org.eclipse.jubula.client.core.model.IObjectMappingCategoryPO;
import org.eclipse.jubula.client.core.model.IObjectMappingPO;
import org.eclipse.jubula.client.core.model.IObjectMappingProfilePO;
import org.eclipse.jubula.client.core.model.IParamDescriptionPO;
import org.eclipse.jubula.client.core.model.IParamNodePO;
import org.eclipse.jubula.client.core.model.IParameterInterfacePO;
import org.eclipse.jubula.client.core.model.IProjectPO;
import org.eclipse.jubula.client.core.model.IProjectPropertiesPO;
import org.eclipse.jubula.client.core.model.IRefTestSuitePO;
import org.eclipse.jubula.client.core.model.IReusedProjectPO;
import org.eclipse.jubula.client.core.model.ISpecTestCasePO;
import org.eclipse.jubula.client.core.model.ITDManager;
import org.eclipse.jubula.client.core.model.ITestDataCategoryPO;
import org.eclipse.jubula.client.core.model.ITestDataCubePO;
import org.eclipse.jubula.client.core.model.ITestDataPO;
import org.eclipse.jubula.client.core.model.ITestJobPO;
import org.eclipse.jubula.client.core.model.IArchivableTestResultSummary;
import org.eclipse.jubula.client.core.model.ITestResultSummaryPO;
import org.eclipse.jubula.client.core.model.ProjectVersion;
import org.eclipse.jubula.client.core.model.ITestResultSummaryPO.AlmReportStatus;
import org.eclipse.jubula.client.core.model.ITestSuitePO;
import org.eclipse.jubula.client.core.model.IUsedToolkitPO;
import org.eclipse.jubula.client.core.model.NodeMaker;
import org.eclipse.jubula.client.core.model.PoMaker;
import org.eclipse.jubula.client.core.model.ReentryProperty;
import org.eclipse.jubula.client.core.persistence.IExecPersistable;
import org.eclipse.jubula.client.core.persistence.ISpecPersistable;
import org.eclipse.jubula.client.core.persistence.NodePM;
import org.eclipse.jubula.client.core.persistence.PersistenceUtil;
import org.eclipse.jubula.client.core.persistence.Persistor;
import org.eclipse.jubula.client.core.persistence.TestResultSummaryPM;
import org.eclipse.jubula.client.core.progress.IProgressConsole;
import org.eclipse.jubula.client.core.utils.AbstractNonPostOperatingTreeNodeOperation;
import org.eclipse.jubula.client.core.utils.ITreeTraverserContext;
import org.eclipse.jubula.client.core.utils.LocaleUtil;
import org.eclipse.jubula.client.core.utils.ModelParamValueConverter;
import org.eclipse.jubula.client.core.utils.ReportRuleType;
import org.eclipse.jubula.client.core.utils.TrackingUnit;
import org.eclipse.jubula.client.core.utils.TreeTraverser;
import org.eclipse.jubula.toolkit.common.xml.businessprocess.ComponentBuilder;
import org.eclipse.jubula.tools.internal.constants.StringConstants;
import org.eclipse.jubula.tools.internal.exception.Assert;
import org.eclipse.jubula.tools.internal.exception.InvalidDataException;
import org.eclipse.jubula.tools.internal.exception.JBVersionException;
import org.eclipse.jubula.tools.internal.i18n.CompSystemI18n;
import org.eclipse.jubula.tools.internal.jarutils.IVersion;
import org.eclipse.jubula.tools.internal.messagehandling.MessageIDs;
import org.eclipse.jubula.tools.internal.objects.ComponentIdentifier;
import org.eclipse.jubula.tools.internal.objects.IComponentIdentifier;
import org.eclipse.jubula.tools.internal.objects.IMonitoringValue;
import org.eclipse.jubula.tools.internal.objects.MonitoringValue;
import org.eclipse.jubula.tools.internal.xml.businessmodell.Component;
import org.eclipse.jubula.tools.internal.xml.businessmodell.ConcreteComponent;
import org.eclipse.jubula.tools.internal.xml.businessmodell.ToolkitDescriptor;
import org.eclipse.osgi.util.NLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thoughtworks.xstream.converters.ConversionException;

/**
 * @author BREDEX GmbH
 * @created 13.01.2006
 */
class XmlImporter {
    /**
     * Pattern for date serialization format.
     * Due to the fact that this constant is used for serialization and 
     * deserialization, it is very important that:<ul>
     *   <li><b>any</b> change to the format be carefully considered such that 
     *     deserialization of dates from exported projects still works</li>
     *   <li>the format <b>only</b> contains numbers, as language differences
     *     might otherwise break deserialization (e.g. American English has 
     *     no way to meaningfully parse the German "Mai")</li>
     * </ul>
     */
    public static final String DATE_PATTERN = "yyyy-MM-dd hh:mm:ss.S"; //$NON-NLS-1$

    /** 
     * Utility for converting between Strings and model objects for use in 
     * import/export operations. This is available for use but should not be 
     * modified (registration/deregistration of converters).  
     */
    public static final BeanUtilsBean BEAN_UTILS = new BeanUtilsBean();
    
    static {
        DateLocaleConverter converter = 
            new DateLocaleConverter(Locale.getDefault(), DATE_PATTERN);
        converter.setLenient(true);
        final SimpleDateFormat dateFormatter = 
            new SimpleDateFormat(DATE_PATTERN);
        Converter stringConverter = new Converter() {
            
            @SuppressWarnings({ "rawtypes", "unused" })
            protected Class getDefaultType() {
                return String.class;
            }
            
            
            @SuppressWarnings({ "rawtypes", "unused" })
            protected Object convertToType(
                    Class type, Object value) throws Throwable {
                return value.toString();
            }
            
            
            protected String convertToString(Object value) throws Throwable {
                if (value instanceof Date) {
                    return dateFormatter.format((Date)value);
                }
                return value.toString();
            }

            public Object convert(Class arg0, Object arg1) {
                if (arg0.isAssignableFrom(String.class)) {
                    try {
                        return convertToString(arg1);
                    } catch (Throwable e) {
                        throw new ConversionException(e);
                    }
                }
                if (arg0.isAssignableFrom(AlmReportStatus.class)) {
                    try {
                        return AlmReportStatus.valueOf(String.valueOf(arg1));
                    } catch (Throwable e) {
                        throw new ConversionException(e);
                    }
                }
                throw new ConversionException("Type " + arg0.getCanonicalName() //$NON-NLS-1$
                    + " not supported for conversion."); //$NON-NLS-1$
            }
        };
        
        ConvertUtilsBean convertUtils = BEAN_UTILS.getConvertUtils();
        convertUtils.register(stringConverter, String.class);
        convertUtils.register(converter, Date.class);
    }
    
    /** number of characters of a GUID */
    private static final int GUID_LENGTH = 32; 
    
    /** standard logging */
    private static Logger log = LoggerFactory.getLogger(XmlImporter.class);
    
    /** Remember which instance belongs to the id used in the XML element */
    private Map<String, IAUTConfigPO> m_autConfRef = 
        new HashMap<String, IAUTConfigPO>();

    /** Remember which instance belongs to the id used in the XML element */
    private Map<String, IAUTMainPO> m_autRef = 
        new HashMap<String, IAUTMainPO>();
    
    /** Remember which instance belongs to the id/guid used in the XML element */
    private Map<String, ISpecTestCasePO> m_tcRef = 
        new HashMap<String, ISpecTestCasePO>();
    
    /** Remember which instance belongs to the id/guid used in the XML element */
    private Map<String, ICategoryPO> m_execCategoryCache = 
        new HashMap<String, ICategoryPO>();
    
    /** Mapping between old and new GUIDs. Only used when assigning new GUIDs */
    private Map<String, String> m_oldToNewGuids = 
        new HashMap<String, String>();

    /** The progress monitor for this importer. */
    private IProgressMonitor m_monitor;
    
    /** The import output. */
    private IProgressConsole m_io;

    /** Parameters that could not be parsed during import */
    private List<String> m_unparseableParameters = new ArrayList<String>();

    /** whether to skip the import of tracked data */
    private boolean m_skipTrackingInformation = false;
    
    /**
     * Constructor
     * 
     * @param monitor
     *            The progress monitor for this import operation.
     * @param io
     *            the import output device during import progress
     * @param skipTrackingInformation
     *            whether to skip the import of tracked data
     */
    public XmlImporter(IProgressMonitor monitor, IProgressConsole io,
            boolean skipTrackingInformation) {
        m_monitor = monitor;
        m_io = io;
        m_skipTrackingInformation  = skipTrackingInformation;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the XML
     * element used as parameter. The method generates all dependent objects as
     * well.
     * 
     * @param xml
     *            Abstraction of the XML element (see Apache XML Beans)
     * @param paramNameMapper
     *            mapper to resolve param names
     * @param compNameCache
     *            cache to resolve component names
     * @return a persistent object generated from the information in the XML
     *         element
     * @throws InvalidDataException
     *             if some data is invalid when constructing an object. This
     *             should not happen for exported project, but may happen when
     *             someone generates XML project description outside of
     *             GUIdancer.
     * @throws JBVersionException
     *             in case of version conflict between used toolkits of imported
     *             project and the installed Toolkit Plugins
     * @throws InterruptedException
     *             if the operation was canceled.
     */
    public IProjectPO createProject(Project xml, 
            IParamNameMapper paramNameMapper, 
            IWritableComponentNameCache compNameCache) 
        throws InvalidDataException, JBVersionException, InterruptedException {
        
        return createProject(xml, false, paramNameMapper, compNameCache);
    }

    /**
     * Creates the instance of the persistent object which is defined by the XML
     * element used as parameter. The method generates all dependend objects as
     * well. This method also assigns a new version number to the persistent
     * object.
     * 
     * @param xml
     *            Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid
     *            if it should be assigned a new guid
     * @param majorVersion
     *            Major version number for the created object.
     * @param minorVersion
     *            Minor version number for the created object.
     * @param microVersion
     *            Micro version number for the created object.
     * @param versionQualifier
     *            Version qualifier number for the created object.
     * @param paramNameMapper
     *            mapper to resolve param names
     * @param compNameCache
     *            cache to resolve component names
     * @return a persistent object generated from the information in the XML
     *         element
     * @throws InvalidDataException
     *             if some data is invalid when constructing an object. This
     *             should not happen for exported project, but may happen when
     *             someone generates XML project description outside of
     *             GUIdancer.
     * @throws JBVersionException
     *             in case of version conflict between used toolkits of imported
     *             project and the installed Toolkit Plugins
     * @throws InterruptedException
     *             if the operation was canceled.
     */
    public IProjectPO createProject(Project xml, boolean assignNewGuid,
        Integer majorVersion, Integer minorVersion,
        Integer microVersion, String versionQualifier,
        IParamNameMapper paramNameMapper, 
        IWritableComponentNameCache compNameCache) 
        throws InvalidDataException, JBVersionException, InterruptedException {
        
        if (majorVersion != null) {
            xml.setMajorProjectVersion(majorVersion);
        } else {
            xml.setNilMajorProjectVersion();
        }
        if (minorVersion != null) {
            xml.setMinorProjectVersion(minorVersion);
        } else {
            xml.setNilMinorProjectVersion();
        }
        if (microVersion != null) {
            xml.setMicroProjectVersion(microVersion);
        } else {
            xml.setNilMicroProjectVersion();
        }
        xml.setProjectVersionQualifier(versionQualifier);
        return createProject(xml, assignNewGuid, paramNameMapper,
                compNameCache);
    }

    /**
     * Creates the instance of the persistent object which is defined by the XML
     * element used as prameter. The method generates all dependend objects as
     * well.
     * 
     * @param xml
     *            Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid
     *            <code>true</code> if the project and all subnodes should be
     *            assigned new GUIDs. Otherwise <code>false</code>.
     * @param paramNameMapper
     *            mapper to resolve param names
     * @param compNameCache
     *            cache to resolve component names
     * @return a persistent object generated from the information in the XML
     *         element
     * @throws InvalidDataException
     *             if some data is invalid when constructing an object. This
     *             should not happen for exported project, but may happen when
     *             someone generates XML project description outside of
     *             GUIdancer.
     * @throws InterruptedException
     *             if the operation was canceled
     * @throws JBVersionException
     *             in case of version conflict between used toolkits of imported
     *             project and the installed Toolkit Plugins
     */
    public IProjectPO createProject(Project xml, boolean assignNewGuid,
        IParamNameMapper paramNameMapper, 
        IWritableComponentNameCache compNameCache) 
        throws InvalidDataException, 
        InterruptedException, JBVersionException {
        
        checkMinimumRequiredXMLVersion(xml);
        documentRequiredProjects(xml);
        checkUsedToolkits(xml);
        
        List<AbstractXmlConverter> listOfConverter = 
            new LinkedList<AbstractXmlConverter>();
        
        // ======= register converter here =======
        listOfConverter.add(new AutIdGenerationConverter());
        listOfConverter.add(new V4C001());
        listOfConverter.add(new HTMLTechnicalComponentIndexConverter());
        listOfConverter.add(new WinToolkitIdConverter());
        listOfConverter.add(new TreeDirectionConverter());
        listOfConverter.add(new RefTSNameConverter());
        // =======================================
        
        for (IXmlConverter c : listOfConverter) {
            c.convert(xml);
        }
        
        IProjectPO proj = create(xml, assignNewGuid, paramNameMapper,
                compNameCache);
        
        if (!m_unparseableParameters.isEmpty()) {
            m_io.writeErrorLine(Messages.UnparseableParameters);
            for (String param : m_unparseableParameters) {
                m_io.writeErrorLine(param);
            }
            m_io.writeLine(StringUtils.EMPTY);
        }
        
        return proj;
    }

    /**
     * @param xml
     *            the project xml
     * @throws JBVersionException
     *             in case of version conflict between given xml and minimum xml
     *             version number; if these versions do not fit the current
     *             available converter are not able to convert the given project
     *             xml properly.
     */
    private void checkMinimumRequiredXMLVersion(Project xml)
        throws JBVersionException {
        if (!xml.isSetMetaDataVersion()
                || xml.getMetaDataVersion() 
                    < IVersion.JB_CLIENT_MIN_XML_METADATA_VERSION) {
            List<String> errorMsgs = new ArrayList<String>();
            errorMsgs.add(Messages.XmlImporterProjectXMLTooOld);
            throw new JBVersionException(
                    Messages.XmlImporterProjectXMLTooOld,
                    MessageIDs.E_LOAD_PROJECT_XML_VERSION_ERROR,
                    errorMsgs);
        }
    }

    /**
     * @param xml
     *            the xml project
     * @throws JBVersionException
     *             in case of version conflict between used toolkits of imported
     *             project and the installed Toolkit Plugins
     */
    private void checkUsedToolkits(Project xml) throws JBVersionException {
        Set<IUsedToolkitPO> usedTK = new HashSet<IUsedToolkitPO>();
        for (UsedToolkit usedToolkit : xml.getUsedToolkitList()) {
            usedTK.add(PoMaker.createUsedToolkitsPO(usedToolkit.getName(), 
                usedToolkit.getMajorVersion(), 
                usedToolkit.getMinorVersion(), 
                null));
        }
        List<String> errorMsgs = new ArrayList<String>();
        if (!validateToolkitVersion(usedTK, xml.getName(), errorMsgs)) {
            throw new JBVersionException(
                Messages.IncompatibleToolkitVersion,
                MessageIDs.E_LOAD_PROJECT_TOOLKIT_MAJOR_VERSION_ERROR, 
                errorMsgs);
        }
    }

    /**
     * @param xml
     *            the datasource to get additional information from
     */
    private void documentRequiredProjects(Project xml) {
        if (xml.getReusedProjectsList().size() > 0) {
            m_io.writeLine(NLS.bind(Messages.XmlImporterProjectDependency,
                    new Object[] { xml.getName(),
                        getProjectVersion(
                                xml.isNilMajorProjectVersion()
                                || !xml.isSetMajorProjectVersion() ? null
                                        : xml.getMajorProjectVersion(),
                                xml.isNilMinorProjectVersion()
                                || !xml.isSetMinorProjectVersion() ? null
                                        : xml.getMinorProjectVersion(),
                                xml.isNilMicroProjectVersion()
                                || !xml.isSetMicroProjectVersion() ? null
                                        : xml.getMicroProjectVersion(),
                            xml.getProjectVersionQualifier())}));
            for (ReusedProject rp : xml.getReusedProjectsList()) {
                ProjectVersion version = getProjectVersion(
                        rp.isNilMajorProjectVersion()
                        || !rp.isSetMajorProjectVersion() ? null
                                : rp.getMajorProjectVersion(),
                        rp.isNilMinorProjectVersion()
                        || !rp.isSetMinorProjectVersion() ? null
                                : rp.getMinorProjectVersion(),
                        rp.isNilMicroProjectVersion()
                        || !rp.isSetMicroProjectVersion() ? null
                                : rp.getMicroProjectVersion(),
                        rp.getProjectVersionQualifier());
                String requiredProjectString = rp.getProjectName() != null 
                    ? NLS.bind(Messages.XmlImporterRequiredProject,
                            new Object[] { rp.getProjectName(), version})
                    : NLS.bind(Messages.XmlImporterRequiredProjectWithoutName,
                            new Object[] { rp.getProjectGUID(), version});
                m_io.writeLine(requiredProjectString);
            }
        }
        
    }
    
    /**
     * Gets the {@link ProjectVersion} for the specified version numbers
     * @param major number
     * @param minor number
     * @param micro number
     * @param versionQualifier string version
     * @return the projectVersion
     */
    private ProjectVersion getProjectVersion(Integer major, Integer minor,
            Integer micro, String versionQualifier) {
        return new ProjectVersion(major, minor, micro, versionQualifier);
    }

    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all dependent objects
     * as well.
     * @param xmlProj the XML-Project
     * @param proj the IProjectPO
     * @param compNameCache The cache for storing and retrieving 
     *                      Component Names in memory.
     * @param assignNewGuid <code>true</code> if the project and all subnodes
     *      should be assigned new GUIDs. Otherwise <code>false</code>.
     */
    private void createComponentNames(Project xmlProj, IProjectPO proj, 
            IWritableComponentNameCache compNameCache, boolean assignNewGuid) {
        
        final List<ComponentName> componentNamesList = xmlProj
            .getComponentNamesList();
        final Map<String, String> oldToNewGUID = new HashMap<String, String>(
                componentNamesList.size());
        Set<IComponentNamePO> createdCompNames = 
            new HashSet<IComponentNamePO>();
        for (ComponentName compName : componentNamesList) {
            String guid = compName.getGUID();
            if (assignNewGuid) {
                final String newGuid = PersistenceUtil.generateGuid();
                oldToNewGUID.put(guid, newGuid);
                guid = newGuid;
            }
            final String name = compName.getCompName();
            final String type = compName.getCompType();
            if (!componentHasDefaultMapping(type)) {
                
                final String creationContext = compName.getCreationContext();
                final CompNameCreationContext ctx = CompNameCreationContext
                    .forName(creationContext);
                final IComponentNamePO componentNamePO = PoMaker
                    .createComponentNamePO(guid, name, type, ctx, proj.getId());
                componentNamePO.setReferencedGuid(compName.getRefGuid());
                createdCompNames.add(componentNamePO);
                compNameCache.addComponentNamePO(
                        componentNamePO);
            }
        }
        
        if (assignNewGuid) {
            for (IComponentNamePO createdName : createdCompNames) {
                String newGuid = oldToNewGUID.get(
                        createdName.getReferencedGuid());
                if (newGuid != null) {
                    createdName.setReferencedGuid(newGuid);
                }
            }
            switchCompNamesGuids(proj, oldToNewGUID);
        }
    }
    
    /**
     * @param proj the IProjectPO
     * @param oldToNewGUID a Map with old to new GUID.
     */
    private void switchCompNamesGuids(IProjectPO proj, 
            final Map<String, String> oldToNewGUID) {
        /** */
        class SwitchCompNamesGuidsOp 
            extends AbstractNonPostOperatingTreeNodeOperation<INodePO> {
            /** {@inheritDoc} */
            public boolean operate(ITreeTraverserContext<INodePO> ctx, 
                    INodePO parent, INodePO node, boolean alreadyVisited) {
                if (node instanceof ICapPO) {
                    switchCapCompNameGuids((ICapPO)node);
                } else if (node instanceof IExecTestCasePO) {
                    switchExecTcCompNameGuids((IExecTestCasePO)node);
                }
                return true;
            }
            /**
             * @param execTc an IExecTestCasePO
             */
            private void switchExecTcCompNameGuids(IExecTestCasePO execTc) {
                for (ICompNamesPairPO pair : new ArrayList<ICompNamesPairPO>(
                        execTc.getCompNamesPairs())) {
                    final String oldGuid = pair.getFirstName();
                    final String newGuid = oldToNewGUID.get(oldGuid);
                    if (newGuid != null) {
                        pair.setFirstName(newGuid);
                        execTc.removeCompNamesPair(oldGuid);
                        execTc.addCompNamesPair(pair);
                    }
                    final String oldSecGuid = pair.getSecondName();
                    final String newSecGuid = oldToNewGUID.get(oldSecGuid);
                    if (newSecGuid != null) {
                        pair.setSecondName(newSecGuid);
                    }
                }
            }
            /**
             * @param cap an IcapPO
             */
            private void switchCapCompNameGuids(ICapPO cap) {
                final String oldGuid = cap.getComponentName();
                final String newGuid = oldToNewGUID.get(oldGuid);
                if (newGuid != null) {
                    cap.setComponentName(newGuid);
                }
            }
        }
        final SwitchCompNamesGuidsOp switchGuidOp = 
            new SwitchCompNamesGuidsOp();
        TreeTraverser ttv = new TreeTraverser(proj, switchGuidOp, true);
        ttv.traverse(true);
        ttv = new TreeTraverser(proj, switchGuidOp, false);
        ttv.traverse(true);
        for (IAUTMainPO autMain : proj.getAutMainList()) {
            final IObjectMappingPO objMap = autMain.getObjMap();
            for (IObjectMappingAssoziationPO oma : objMap.getMappings()) {
                List<String> namesToUpdate = new ArrayList<String>();
                for (String oldLogicName : oma.getLogicalNames()) {
                    if (oldToNewGUID.containsKey(oldLogicName)) {
                        namesToUpdate.add(oldLogicName);
                    }
                }
                for (String oldLogicName : namesToUpdate) {
                    oma.removeLogicalName(oldLogicName);
                    oma.addLogicalName(oldToNewGUID.get(oldLogicName));
                }
            }
        }
    }

    /**
     * @param usedTK toolkits used from project to import
     * @param projName name of project to import
     * @param errorMsgs list with strings of detailed error messages
     * @return if project uses toolkits which client supports 
     */
    private boolean validateToolkitVersion(Set<IUsedToolkitPO> usedTK, 
        String projName, List<String> errorMsgs) {
        List<ToolkitPluginError> errors = 
            UsedToolkitBP.getInstance().checkUsedToolkitPluginVersions(usedTK);
        if (errors.isEmpty()) {
            return true;
        } 
        boolean loadProject = true;        
        for (ToolkitPluginError error : errors) {
            final StringBuilder strBuilder = new StringBuilder();
            String toolkitId = error.getToolkitId();
            ToolkitDescriptor desc = 
                ComponentBuilder.getInstance().getCompSystem()
                .getToolkitDescriptor(toolkitId);
            String toolkitName = desc != null ? desc.getName() : toolkitId;
            strBuilder.append(Messages.OpenProjectActionToolkitVersionConflict2)
                .append(toolkitName)
                .append(Messages.XmlImporterToolkitVersionConflict3a)
                .append(projName)
                .append(Messages.XmlImporterToolkitVersionConflict3b);
            
            final ERROR errorType = error.getError();
            final String descr = Messages
                .OpenProjectActionToolkitVersionConflict5;
            switch (errorType) {
                case MAJOR_VERSION_ERROR:
                    strBuilder.append(Messages
                            .OpenProjectActionToolkitVersionConflict4a);
                    strBuilder.append(descr);
                    errorMsgs.add(strBuilder.toString());
                    loadProject = false;
                    break;
    
                case MINOR_VERSION_HIGHER:
                    strBuilder.append(Messages
                            .OpenProjectActionToolkitVersionConflict4b);
                    strBuilder.append(descr);
                    errorMsgs.add(strBuilder.toString());
                    loadProject = false;
                    
                    break;
                    
                case MINOR_VERSION_LOWER:
                    break;
                    
                default:
                    Assert.notReached(Messages.UnknownErrorType
                        + String.valueOf(errorType));
            }
        }
        return loadProject;
        
    }

    /**
     * 
     * @param xml XML
     * @param assignNewGuid <code>true</code> if the project and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @param mapper a mapper
     * @param cNC the component name cache to use during project creation
     * @return The ProjectPO
     * @throws InvalidDataException
     * @see createProject(Project xml, boolean assignNewGuid,
     *   IParamNameMapper mapper)
     */
    private IProjectPO create(Project xml, boolean assignNewGuid,
        IParamNameMapper mapper, IWritableComponentNameCache cNC) 
        throws InvalidDataException, InterruptedException {
        IProjectPO proj = initProject(xml, assignNewGuid);
        EntityManager attrDescSession = Persistor.instance().openSession();
        try {
            fillProject(proj, xml, attrDescSession, assignNewGuid, mapper, cNC);
        } finally {         
            Persistor.instance().dropSession(attrDescSession);
        }
        return proj;
    }
    
    /**
     * @param proj The project that will be filled.
     * @param attrDescSession The attribute session.
     * @param xml XML
     * @param assignNewGuid <code>true</code> if the project and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @param mapper a mapper
     * @param cNC the component name cache to use during project creation
     * @throws InvalidDataException
     * @see createProject(Project xml, boolean assignNewGuid,
     *   IParamNameMapper mapper)
     */
    private void fillProject(IProjectPO proj, Project xml,
        EntityManager attrDescSession, boolean assignNewGuid, 
        IParamNameMapper mapper, IWritableComponentNameCache cNC)
        throws InvalidDataException, InterruptedException {
        IProjectPropertiesPO projectProperties = 
                fillProjectProperties(proj, xml);
        if (xml.isSetTestResultDetailsCleanupInterval()) {
            proj.setTestResultCleanupInterval(xml
                    .getTestResultDetailsCleanupInterval());
        } else {
            proj.setTestResultCleanupInterval(IProjectPO.CLEANUP_DEFAULT);
        }
        for (ReusedProject reusedProj : xml.getReusedProjectsList()) {
            checkCancel();
            proj.addUsedProject(createReusedProject(reusedProj));
        }
        for (String projLang : xml.getProjectLanguageList()) {
            proj.getLangHelper().addLanguageToList(
                    LocaleUtil.convertStrToLocale(projLang));
        }
        for (Aut autXml : xml.getAutList()) {
            checkCancel();
            proj.addAUTMain(createAUTMain(autXml, assignNewGuid));
        }
        for (TestDataCategory testDataCategory 
                : xml.getTestDataCategoryList()) {
            checkCancel();
            proj.getTestDataCubeCont().addCategory(createTestDataCategory(
                    testDataCategory, assignNewGuid, mapper));
        }
        for (NamedTestData testDataCube : xml.getNamedTestDataList()) {
            checkCancel();
            proj.getTestDataCubeCont().addTestData(createTestDataCube(
                    testDataCube, assignNewGuid, mapper));
        }
        for (Category catXml : xml.getCategoryList()) {
            checkCancel();
            proj.getSpecObjCont().addSpecObject(
                    createCategory(proj, catXml, assignNewGuid, mapper));
        }
        for (TestCase tcXml : xml.getTestcaseList()) {
            checkCancel();
            initTestCase(assignNewGuid, mapper, proj, tcXml);
        }

        for (Category catXml : xml.getCategoryList()) {
            checkCancel();
            rerunCategories(proj, catXml, assignNewGuid, attrDescSession);
        }
        for (TestCase tcXml : xml.getTestcaseList()) {
            checkCancel();
            completeTestCase(proj, tcXml, assignNewGuid, attrDescSession);
        }
        // BEGIN - pre 1.2 xml data model handling
        handleOldTestSuitesAndTestJobs(proj, xml, attrDescSession,
                assignNewGuid);
        // END - pre 1.2 xml data model handling
        handleTestSuitesAndTestJobsAndCategories(proj, xml, assignNewGuid);
        
        for (CheckConfiguration xmlConf : xml.getCheckConfigurationList()) {
            checkCancel();
            initCheckConf(
                xmlConf, projectProperties.getCheckConfCont());
        }
        if (xml.getTestresultSummaries() != null) {
            initTestResultSummaries(xml.getTestresultSummaries(), proj);
        }
        createComponentNames(xml, proj, cNC, assignNewGuid);
    }

    /**
     * @param proj
     *            the project
     * @param xml
     *            the project
     * @return the project properties
     */
    public IProjectPropertiesPO fillProjectProperties(IProjectPO proj,
            Project xml) {
        proj.setComment(xml.getComment());
        proj.setMarkupLanguage(xml.getMarkupLanguage());
        proj.setDefaultLanguage(LocaleUtil.convertStrToLocale(xml
                .getDefaultLanguage()));
        proj.setToolkit(xml.getAutToolKit());
        proj.setIsReusable(xml.getIsReusable());
        proj.setIsProtected(xml.getIsProtected());
        IProjectPropertiesPO projProperties = proj.getProjectProperties();
        projProperties.setALMRepositoryName(xml.getAlmRepositoryName());
        projProperties.setIsReportOnSuccess(xml.getIsReportOnSuccess());
        projProperties.setIsReportOnFailure(xml.getIsReportOnFailure());
        projProperties.setDashboardURL(xml.getDashboardURL());
        projProperties.getCheckConfCont().setEnabled(
                xml.getTeststyleEnabled());
        
        projProperties.setIsTrackingActivated(xml.getTrackingEnabled());
        projProperties.setTrackChangesSignature(xml.getTrackingAttribute());
        if (xml.isSetTrackingUnit()) {
            projProperties.setTrackChangesUnit(
                    TrackingUnit.valueOf(xml.getTrackingUnit()));
        }
        projProperties.setTrackChangesSpan(xml.getTrackingSpan());
        
        List<IALMReportingRulePO> reportingRules = 
                new ArrayList<IALMReportingRulePO>();
        for (ReportingRule rule : xml.getReportingRulesList()) {
            IALMReportingRulePO newReportingRule = createReportingRule(rule);
            reportingRules.add(newReportingRule);
        }
        projProperties.setALMReportingRules(reportingRules);
        
        return projProperties;
    }

    /**
     * converts alm reporting rule from xml to po
     * @param xml rule from xml file
     * @return the converted rule
     */
    private IALMReportingRulePO createReportingRule(ReportingRule xml) {
        String name = xml.getName();
        String fieldID = xml.getFieldID();
        String value = xml.getValue();
        String xmlType = xml.getType();
        ReportRuleType type = null;
        if (xmlType.equals(ReportRuleType.ONSUCCESS.toString())) {
            type = ReportRuleType.ONSUCCESS;
        } else if (xmlType.equals(ReportRuleType.ONFAILURE.toString())) {
            type = ReportRuleType.ONFAILURE;
        }
        IALMReportingRulePO rule = PoMaker.createALMReportingRulePO(
                name, fieldID, value, type);
        return rule;
    }

    /**
     * @param proj
     *            the project po
     * @param xml
     *            the project xml
     * @param assignNewGuid
     *            flag to indicate whether new ids should be assigned
     * @throws InterruptedException
     *             in case of interruption
     * @throws InvalidDataException
     *             in case of invalid data
     */
    private void handleTestSuitesAndTestJobsAndCategories(IProjectPO proj,
            Project xml, boolean assignNewGuid) throws InterruptedException,
            InvalidDataException {
        for (ExecCategory catXml : xml.getExecCategoriesList()) {
            checkCancel();
            List<IExecPersistable> tsAndCats = 
                    createListOfCategoriesAndTestsuites(
                    proj, catXml, assignNewGuid);
            for (IExecPersistable exec : tsAndCats) {
                proj.getExecObjCont().addExecObject(exec);
            }
        }
        for (ExecCategory catXml : xml.getExecCategoriesList()) {
            checkCancel();
            List<IExecPersistable> tjs = createListOfTestJobs(catXml,
                    assignNewGuid);
            for (IExecPersistable exec : tjs) {
                proj.getExecObjCont().addExecObject(exec);
            }
        }
    }

    /**
     * Handle "old"-XML data structure for pre 1.2 datamodel
     * 
     * @param proj
     *            the project
     * @param xml
     *            the project xml
     * @param attrDescSession
     *            the attribute description
     * @param assignNewGuid
     *            whether new GUIDs should be assigned or not
     * @throws InterruptedException
     *             in case of an interruption
     * @throws InvalidDataException
     *             if some data is invalid when constructing an object. This
     *             should not happen for exported project, but may happen when
     *             someone generates XML project description outside of
     *             GUIdancer.
     */
    private void handleOldTestSuitesAndTestJobs(IProjectPO proj, Project xml,
        EntityManager attrDescSession, boolean assignNewGuid)
        throws InterruptedException, InvalidDataException {
        if (!xml.getTestsuiteList().isEmpty()) {
            ICategoryPO catTS = NodeMaker.createCategoryPO("Test Suites"); //$NON-NLS-1$
            for (TestSuite tsXml : xml.getTestsuiteList()) {
                checkCancel();
                ITestSuitePO tsPO = createTestSuite(proj, tsXml, assignNewGuid);
                catTS.addNode(tsPO);
            }
            proj.getExecObjCont().addExecObject(catTS);
        }
        if (!xml.getTestsuiteList().isEmpty()) {
            ICategoryPO catTJ = NodeMaker.createCategoryPO("Test Jobs"); //$NON-NLS-1$
            for (TestJobs tjXml : xml.getTestJobsList()) {
                checkCancel();
                catTJ.addNode(createTestJob(tjXml, assignNewGuid));
            }
            proj.getExecObjCont().addExecObject(catTJ);
        }
    }

    /**
     * @param xmlConf
     *            The source of the check configuration
     * @param checkConfCont
     *            The destiny of the check configuration (will be persisted)
     */
    private void initCheckConf(CheckConfiguration xmlConf,
            ICheckConfContPO checkConfCont) {
        if (xmlConf.getSeverity().matches("(0|1|2|3)")) { //$NON-NLS-1$
            return; // its an old exported xml, just don't create the conf
        }
        ICheckConfPO chkConf = checkConfCont.createCheckConf();
        chkConf.setSeverity(xmlConf.getSeverity());
        chkConf.setActive(xmlConf.getActivated());
        
        for (CheckAttribute xmlAttr : xmlConf.getCheckAttributeList()) {
            chkConf.getAttr().put(xmlAttr.getName(), xmlAttr.getValue());
        }
        for (CheckActivatedContext xmlCxt : xmlConf.getActiveContextList()) {
            boolean active = xmlCxt.getActive();
            chkConf.getContexts().put(xmlCxt.getClass1(), active);
        }
        
        checkConfCont.addCheckConf(xmlConf.getCheckId(), chkConf);
    }

    /**
     * @param xml XML storage for the project
     * @param assignNewGuid <code>true</code> if the project and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a new IProjectPO
     */
    private IProjectPO initProject(Project xml, boolean assignNewGuid) {
        IProjectPO proj = null;
        if (xml.getGUID() != null) {
            Integer majorProjVersion = null;
            if (!xml.isNilMajorProjectVersion()
                    && xml.isSetMajorProjectVersion()
                    || xml.isSetMajorNumber()) {
                majorProjVersion = xml.isSetMajorNumber()
                        ? xml.getMajorNumber() : xml.getMajorProjectVersion();
            }
            Integer minorProjVersion = null;
            if (!xml.isNilMinorProjectVersion()
                    && xml.isSetMinorProjectVersion()
                    || xml.isSetMinorNumber()) {
                minorProjVersion = xml.isSetMinorNumber()
                        ? xml.getMinorNumber() : xml.getMinorProjectVersion();
            }
            Integer microProjVersion = !xml.isSetMicroProjectVersion()
                    || xml.isNilMicroProjectVersion() ? null : xml
                    .getMicroProjectVersion();
            String postFixProjVersion = !xml.isSetProjectVersionQualifier()
                    || xml.isNilProjectVersionQualifier() ? null : xml
                    .getProjectVersionQualifier();
            if (!assignNewGuid) {
                proj = NodeMaker.createProjectPO(
                        IVersion.JB_CLIENT_METADATA_VERSION, majorProjVersion,
                        minorProjVersion, microProjVersion, postFixProjVersion,
                        xml.getGUID());
            } else {
                proj = NodeMaker.createProjectPO(
                        IVersion.JB_CLIENT_METADATA_VERSION, majorProjVersion,
                        minorProjVersion, microProjVersion, postFixProjVersion);
            }
            ProjectNameBP.getInstance().setName(xml.getGUID(), xml.getName(),
                    false);
        } else {
            proj = NodeMaker.createProjectPO(xml.getName(), IVersion
                .JB_CLIENT_METADATA_VERSION);
            if (assignNewGuid) {
                m_oldToNewGuids.put(xml.getGUID(), proj.getGuid());
            }
        }
        return proj;
    }

    /**
     * @param proj The project to which the test result summaries belongs.
     * @param trsListXml
     *            The XML element for the test result summaries
     */
    private void initTestResultSummaries(
            TestresultSummaries trsListXml, IProjectPO proj) {
        
        PropertyDescriptor[] propertiesToImport = 
            BEAN_UTILS.getPropertyUtils().getPropertyDescriptors(
                    IArchivableTestResultSummary.class);

        for (TestresultSummary trsXml : trsListXml.getTestresultSummaryList()) {
        
            ITestResultSummaryPO summary = PoMaker.createTestResultSummaryPO();
            summary.setInternalProjectGuid(proj.getGuid());
            
            for (PropertyDescriptor pd : propertiesToImport) {
                List<SummaryAttribute> entries = trsXml.getAttributeList();
                String propertyNameToSet = pd.getName();
                boolean found = false;
                int pos = 0;
                for (SummaryAttribute me : entries) {
                    if (me.getKey().equals(propertyNameToSet)) {
                        found = true;
                        break;
                    }
                    pos++;
                }
                if (found) {
                    SummaryAttribute sa = entries.get(pos);
                    if (!sa.isNilValue()) {
                        try {
                            BEAN_UTILS.setProperty(summary, 
                                    propertyNameToSet, sa.getValue());
                        } catch (IllegalAccessException e) {
                            log.warn(e.getLocalizedMessage(), e);
                        } catch (InvocationTargetException e) {
                            log.warn(e.getLocalizedMessage(), e);
                        }
                    }
                } else {
                    log.warn(Messages.Property + StringConstants.SPACE
                            + propertyNameToSet + StringConstants.SPACE
                            + Messages.NotFound + StringConstants.DOT);
                }
            }
            
            List<MonitoringValues> tmpList = trsXml.getMonitoringValueList();
            Map<String, IMonitoringValue> tmpMap = 
                new HashMap<String, IMonitoringValue>();            
            for (int i = 0; i < tmpList.size(); i++) {
                MonitoringValues tmpMon = tmpList.get(i);
                MonitoringValue tmp = new MonitoringValue();
                tmp.setCategory(tmpMon.getCategory());
                tmp.setSignificant(tmpMon.getIsSignificant());
                tmp.setType(tmpMon.getType());
                tmp.setValue(tmpMon.getValue());
                tmpMap.put(tmpMon.getKey(), tmp);                
                
            }            
            summary.setMonitoringValues(tmpMap);            
            
            if (!TestResultSummaryPM.doesTestResultSummaryExist(summary)) {
                TestResultSummaryPM.storeTestResultSummaryInDB(summary);
            }

        
        }
            
    }

    /**
     * Creates and initializes a test case.
     * 
     * @param assignNewGuid <code>true</code> if the test case and all 
     *                      sub-elements should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @param mapper Mapper to resolve param names.
     * @param proj The project to which the test case belongs.
     * @param tcXml The XML element for the test case.
     */
    private void initTestCase(boolean assignNewGuid, IParamNameMapper mapper,
            IProjectPO proj, TestCase tcXml) {
        ISpecTestCasePO tcPO = 
            createTestCaseBase(proj, tcXml, assignNewGuid, mapper);
        proj.getSpecObjCont().addSpecObject(tcPO);
    }

    /**
     * Checks whether the operation has been canceled. If the operation has been
     * canceled, an <code>InterruptedException</code> will be thrown.
     * 
     * @throws InterruptedException if the operation has been canceled.
     */
    private void checkCancel() throws InterruptedException {
        if (m_monitor.isCanceled()) {
            throw new InterruptedException();
        }
    }
    
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the testcase and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @param attrDescSession The session used for locating attribute 
     *                        descriptions in the database.
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private ISpecTestCasePO completeTestCase(IProjectPO proj,
        TestCase xml, boolean assignNewGuid, EntityManager attrDescSession) 
        throws InvalidDataException {
        
        ISpecTestCasePO tc;
        
        if (xml.getId() != null) {
            tc = m_tcRef.get(xml.getId());
        } else if (assignNewGuid) {
            tc = m_tcRef.get(m_oldToNewGuids.get(xml.getGUID()));
        } else {
            tc = m_tcRef.get(xml.getGUID());
        }
        
        for (Teststep stepXml : xml.getTeststepList()) {
            if (stepXml.getCap() != null) {
                tc.addNode(createCap(proj, stepXml.getCap(), assignNewGuid));
            } else {
                tc.addNode(createExecTestCase(
                    proj, stepXml.getUsedTestcase(), assignNewGuid));
            }
        }
        for (EventTestCase evTcXml : xml.getEventTestcaseList()) {
            tc.addEventTestCase(createEventExecTestCase(
                proj, tc, evTcXml, assignNewGuid));
        }
        return tc;
    }
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all depended objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the AUT Config
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     */
    private IAUTConfigPO createAUTConfig(AutConfig xml, boolean assignNewGuid) {
        IAUTConfigPO conf = null;
        if (xml.getGUID() != null && !assignNewGuid) {
            // GUID is available
            conf = PoMaker.createAUTConfigPO(xml.getGUID());
        } else {
            conf = PoMaker.createAUTConfigPO();
        }
        m_autConfRef.put(xml.getId(), conf);
        
        final List<MapEntry> confAttrMapList = xml.getConfAttrMapEntryList();
        for (MapEntry entry : confAttrMapList) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            conf.setValue(key, value);
        }
        return conf;
    }
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all depended objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the AUT and all corresponding 
     *                      AUT Configs should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     */
    private IAUTMainPO createAUTMain(Aut xml, boolean assignNewGuid) {
        IAUTMainPO aut = null;
        if (xml.getGUID() != null && !assignNewGuid) {
            // GUID is available
            aut = PoMaker.createAUTMainPO(xml.getName(), xml.getGUID());
        } else {
            aut = PoMaker.createAUTMainPO(xml.getName());
        }

        aut.setToolkit(xml.getAutToolkit());
        aut.setGenerateNames(xml.getGenerateNames());
        m_autRef.put(xml.getId(), aut);
        aut.setObjMap(createOM(xml));
        for (String lang : xml.getLanguageList()) {
            aut.getLangHelper().addLanguageToList(
                LocaleUtil.convertStrToLocale(lang));
        }
        for (AutConfig confXml : xml.getConfigList()) {
            aut.addAutConfigToSet(createAUTConfig(confXml, assignNewGuid));
        }
        for (String autId : xml.getAutIdList()) {
            aut.getAutIds().add(autId);
        }
        
        return aut;
    }
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @return a persistent object generated from the information in the XML
     * element
     */
    private IReusedProjectPO createReusedProject(ReusedProject xml) {
        Integer majorProjVersion = null;
        if (xml.isSetMajorProjectVersion()) {
            majorProjVersion = xml.isNilMajorProjectVersion() ? null : xml
                    .getMajorProjectVersion();

        } else if (xml.isSetMajorNumber()) {
            majorProjVersion = xml.isNilMajorNumber() ? null : xml
                    .getMajorNumber();
        }

        Integer minorProjVersion = null;
        if (xml.isSetMinorProjectVersion()) {
            minorProjVersion = xml.isNilMinorProjectVersion() ? null : xml
                    .getMinorProjectVersion();
        } else if (xml.isSetMinorNumber()) {
            minorProjVersion = xml.isNilMinorNumber() ? null : xml
                    .getMinorNumber();
        }

        Integer microProjVersion = !xml.isSetMicroProjectVersion()
                || xml.isNilMicroProjectVersion() ? null : xml
                .getMicroProjectVersion();
        String versionQualifier = !xml.isSetProjectVersionQualifier()
                || xml.isNilProjectVersionQualifier() ? null : xml
                .getProjectVersionQualifier();

        IReusedProjectPO reusedProject = PoMaker.createReusedProjectPO(
                xml.getProjectGUID(), majorProjVersion, minorProjVersion,
                microProjVersion, versionQualifier);
        return reusedProject;
    }
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the cap
     *                      should be assigned a new GUID. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     */
    private ICapPO createCap(IProjectPO proj, Cap xml, boolean assignNewGuid) {

        final ICapPO cap;
        String componentname = xml.getComponentName();
        if (componentHasDefaultMapping(xml.getComponentType())) {
            componentname = null;
        }
        if (xml.getGUID() != null && !assignNewGuid) {
            // GUID is available
            cap = NodeMaker.createCapPO(
                xml.getName(), componentname, xml.getComponentType(), 
                xml.getActionName(), proj, xml.getGUID());
        } else {
            cap = NodeMaker.createCapPO(xml.getName(), 
                componentname, xml.getComponentType(), 
                xml.getActionName(), proj);
        }
        cap.setDataFile(xml.getDatafile());
        if (xml.isSetActive()) {
            cap.setActive(xml.getActive());
        } else {
            cap.setActive(true);
        }
        if (xml.getComment() != null) {
            cap.setComment(xml.getComment());
        }
        if (xml.getTestdata() != null) {
            ITDManager tdman = fillTDManager(cap, xml);
            cap.setDataManager(tdman);                
        }
        return cap;
    }

    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the category and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @param mapper mapper to resolve param names
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private ISpecPersistable createCategory(IProjectPO proj, 
        Category xml, boolean assignNewGuid, IParamNameMapper mapper) 
        throws InvalidDataException {
        ICategoryPO cat;
        if (xml.getGUID() != null && !assignNewGuid) {
            cat = NodeMaker.createCategoryPO(xml.getName(), xml.getGUID());
        } else {
            cat = NodeMaker.createCategoryPO(xml.getName());
        }
        cat.setGenerated(xml.getGenerated());
        cat.setComment(xml.getComment());
        cat.setTaskId(xml.getTaskId());
        
        for (Category catXml  : xml.getCategoryList()) {
            cat.addNode(createCategory(proj, catXml, assignNewGuid, mapper));
        }
        
        for (TestCase tcXml : xml.getTestcaseList()) {
            cat.addNode(createTestCaseBase(proj, tcXml, assignNewGuid, mapper));
        }
        return cat;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the category and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private List<IExecPersistable> createListOfCategoriesAndTestsuites(
        IProjectPO proj, ExecCategory xml, boolean assignNewGuid)
        throws InvalidDataException {
        List<IExecPersistable> execNodes = new ArrayList<IExecPersistable>();

        for (ExecCategory catXml : xml.getCategoryList()) {
            execNodes.add(
                    createExecObjects(proj, catXml, assignNewGuid));
        }

        for (TestSuite tsXml : xml.getTestsuiteList()) {
            execNodes.add(createTestSuite(proj, tsXml, assignNewGuid));
        }

        return execNodes;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all dependend objects
     * as well.
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the category and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private List<IExecPersistable> createListOfTestJobs(ExecCategory xml,
            boolean assignNewGuid) throws InvalidDataException {
        List<IExecPersistable> execNodes = new ArrayList<IExecPersistable>();

        for (ExecCategory catXml : xml.getCategoryList()) {
            createTestJobs(catXml, assignNewGuid);
        }
        for (TestJobs tjXml : xml.getTestjobList()) {
            execNodes.add(createTestJob(tjXml, assignNewGuid));
        }
        return execNodes;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all dependend objects
     * as well.
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the category and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private void createTestJobs(ExecCategory xml, boolean assignNewGuid)
        throws InvalidDataException {
        for (ExecCategory catXml : xml.getCategoryList()) {
            createTestJobs(catXml, assignNewGuid);
        }
        
        String guid = xml.getGUID();
        if (assignNewGuid) {
            guid = m_oldToNewGuids.get(guid);
        }
        ICategoryPO cat = m_execCategoryCache.get(guid);
        for (TestJobs tjXml : xml.getTestjobList()) {
            cat.addNode(createTestJob(tjXml, assignNewGuid));
        }
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the category and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private IExecPersistable createExecObjects(IProjectPO proj, 
        ExecCategory xml, boolean assignNewGuid) 
        throws InvalidDataException {
        ICategoryPO cat;
        if (xml.getGUID() != null && !assignNewGuid) {
            cat = NodeMaker.createCategoryPO(xml.getName(), xml.getGUID());
            m_execCategoryCache.put(xml.getGUID(), cat);
        } else {
            cat = NodeMaker.createCategoryPO(xml.getName());
            m_execCategoryCache.put(cat.getGuid(), cat);
            m_oldToNewGuids.put(xml.getGUID(), cat.getGuid());
        }
        cat.setGenerated(xml.getGenerated());
        cat.setComment(xml.getComment());
        cat.setDescription(xml.getDescription());
        cat.setTaskId(xml.getTaskId());
        
        for (ExecCategory catXml  : xml.getCategoryList()) {
            cat.addNode(createExecObjects(proj, catXml, assignNewGuid));
        }
        
        for (TestSuite tsXml : xml.getTestsuiteList()) {
            cat.addNode(
                    createTestSuite(proj, tsXml, assignNewGuid));
        }
        
        return cat;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param tc Testcase which holds the newly created EventExecTC.
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the test case
     *                      should be assigned a new GUID. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private IEventExecTestCasePO createEventExecTestCase(IProjectPO proj,
        ISpecTestCasePO tc, EventTestCase xml, boolean assignNewGuid)
        throws InvalidDataException {

        IEventExecTestCasePO evTc;
        ISpecTestCasePO refTc;
        if (xml.getTestcaseRef() != null) {
            refTc = findReferencedTC(xml.getTestcaseRef());
        } else {
            refTc = findReferencedTCByGuid(xml.getTestcaseGuid(), 
                xml.getProjectGuid(), proj, assignNewGuid);
        }
        
        if (refTc == null) {
            // SpectTC is not yet available in this DB
            if (assignNewGuid) {
                evTc = NodeMaker.createEventExecTestCasePO(
                    xml.getTestcaseGuid(), xml.getProjectGuid(), tc);
            } else {
                evTc = NodeMaker.createEventExecTestCasePO(
                    xml.getTestcaseGuid(), xml.getProjectGuid(), 
                    tc, xml.getGUID());
            }
        } else {
            if (xml.getGUID() != null && !assignNewGuid) {
                evTc = NodeMaker.createEventExecTestCasePO(
                    refTc, tc, xml.getGUID());
            } else {
                evTc = NodeMaker.createEventExecTestCasePO(
                    refTc, tc);
            }
        }
        fillExecTestCase(proj, xml, evTc, assignNewGuid);
        evTc.setEventType(xml.getEventType());
        ReentryProperty reentryProperty = ReentryProperty.getProperty(xml
            .getReentryProperty().intValue());
        evTc.setReentryProp(reentryProperty);
        if (reentryProperty == ReentryProperty.RETRY) {
            evTc.setMaxRetries(xml.isSetMaxRetries() ? xml.getMaxRetries() : 1);
        }
        
        // Clear the cached specTc to avoid LazyInitializationExceptions
        evTc.clearCachedSpecTestCase();
        
        return evTc;
    }

    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the testcase and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     */
    private IExecTestCasePO createExecTestCase(IProjectPO proj,
        RefTestCase xml, boolean assignNewGuid) {
        IExecTestCasePO exec;
        ISpecTestCasePO refTc;
        if (xml.getTestcaseRef() != null) {
            refTc = findReferencedTC(xml.getTestcaseRef());
        } else {
            refTc = findReferencedTCByGuid(xml.getTestcaseGuid(), 
                xml.getProjectGuid(), proj, assignNewGuid);
        }
        
        if (refTc == null) {
            // SpectTC is not yet available in this DB
            if (!assignNewGuid) {
                exec = NodeMaker.createExecTestCasePO(
                    xml.getTestcaseGuid(), xml.getProjectGuid(), xml.getGUID());
            } else {
                exec = NodeMaker.createExecTestCasePO(
                    xml.getTestcaseGuid(), xml.getProjectGuid());
            }
        } else {
            if (xml.getGUID() != null && !assignNewGuid) {
                // GUID is available
                exec = NodeMaker.createExecTestCasePO(refTc, xml.getGUID());
            } else {
                exec = NodeMaker.createExecTestCasePO(refTc);
            }
        }
        
        fillExecTestCase(proj, xml, exec, assignNewGuid);

        // Clear the cached specTc to avoid LazyInitializationExceptions
        exec.clearCachedSpecTestCase();
        
        return exec;
    }
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as parameter. The method generates all dependend objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @return a persistent object generated from the information in the XML
     * element
     */
    private IObjectMappingPO createOM(Aut xml) {
        IObjectMappingPO om = PoMaker.createObjectMappingPO();
        ObjectMapping omXml = xml.getObjectMapping();
        ObjectMappingProfile profileXml = omXml.getProfile();
        if (profileXml != null) {
            // Use the profile defined in the imported project
            IObjectMappingProfilePO profilePo = PoMaker
                    .createObjectMappingProfile();
            profilePo.setContextFactor(profileXml.getContextFactor());
            profilePo.setNameFactor(profileXml.getNameFactor());
            profilePo.setPathFactor(profileXml.getPathFactor());
            profilePo.setThreshold(profileXml.getThreshold());
            om.setProfile(profilePo);
        }

        OmCategory mappedCategoryXml = omXml.getMapped();
        if (mappedCategoryXml != null) {
            fillObjectMappingCategory(
                    mappedCategoryXml, om.getMappedCategory());
        }

        OmCategory unmappedComponentCategory = omXml.getUnmappedComponent();
        if (unmappedComponentCategory != null) {
            fillObjectMappingCategory(unmappedComponentCategory, 
                    om.getUnmappedLogicalCategory());
        }
        
        OmCategory unmappedTechnicalCategory = omXml.getUnmappedTechnical();
        if (unmappedTechnicalCategory != null) {
            fillObjectMappingCategory(unmappedTechnicalCategory, 
                    om.getUnmappedTechnicalCategory());
        }
        
        return om;
    }
    
    /**
     * Write the information from the XML element to its corresponding Object.
     * 
     * @param categoryXml
     *            The XML element which contains the information
     * @param category
     *            The persistent object Object
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void fillObjectMappingCategory(OmCategory categoryXml,
            IObjectMappingCategoryPO category) {

        category.setName(categoryXml.getName());
        for (OmCategory subcategoryXml : categoryXml.getCategoryList()) {
            IObjectMappingCategoryPO subcategory = 
                PoMaker.createObjectMappingCategoryPO(subcategoryXml.getName());
            category.addCategory(subcategory);
            fillObjectMappingCategory(subcategoryXml, subcategory);
        }
        
        for (OmEntry assocXml : categoryXml.getAssociationList()) {
            TechnicalName tecNameXml = assocXml.getTechnicalName();
            List<String> logNames = assocXml.getLogicalNameList();
            IComponentIdentifier tecName = null;
            if (tecNameXml != null && !tecNameXml.isNil()) {
                tecName = new ComponentIdentifier();
                tecName.setComponentClassName(tecNameXml
                    .getComponentClassName());
                tecName.setSupportedClassName(tecNameXml
                    .getSupportedClassName());
                tecName.setAlternativeDisplayName(tecNameXml
                    .getAlternativeDisplayName());
                tecName.setNeighbours(
                        new ArrayList(tecNameXml.getNeighbourList()));
                tecName.setHierarchyNames(
                        new ArrayList(tecNameXml.getHierarchyNameList()));
            }

            // It is necessary to create a new (cloneable) list from the list
            // of component names because the list itself is not cloneable.
            // If the list is used directly, then 
            IObjectMappingAssoziationPO assoc = 
                PoMaker.createObjectMappingAssoziationPO(tecName, 
                        new ArrayList<String>(logNames));
            assoc.setType(assocXml.getType());
            category.addAssociation(assoc);
        }
    }

    /**
     * This method is called if the Action of a CAP is not compatible with the
     * current XML-Config-File.<br>
     * The existent TDManager of the CAP is filled with the TestData
     * @param owner The CAP.
     * @param xmlCap The abstraction of the XML CAP (see Apache XML Beans)
     * @return the filled TDManager of the given owner
     */
    private ITDManager fillTDManager(IParamNodePO owner, Cap xmlCap) {
                
        final ITDManager tdman = owner.getDataManager();
        List<ParamDescription> parDescList = xmlCap
            .getParameterDescriptionList();
        final TestData testData = xmlCap.getTestdata();
        int tdRow = 0;
        for (TestDataRow rowXml : testData.getRowList()) {
            if (rowXml.getDataList().isEmpty()) {
                // Bug http://eclip.se/337215 may have caused Test Steps in exported Projects 
                // to incorrectly contain multiple Data Sets. These erroneous 
                // Data Sets seem to always be empty, so ignore empty Data Sets 
                // for imported Test Steps. 
                continue;
            }
            List<ITestDataPO> tdList = null;
            try {
                tdList = tdman.getDataSet(tdRow).getList();
            } catch (IndexOutOfBoundsException ioobe) {
                // Component, Action, and/or Parameter could not be found in config xml
                // only log and continue -> import of projects with missing plugins
                //FIXME: NLS Look at "CompSystemI18n.getStri[...]"
                final StringBuilder msgSb = new StringBuilder();
                msgSb.append(Messages.Component);
                msgSb.append(StringConstants.COLON + StringConstants.SPACE);
                msgSb.append(xmlCap.getComponentType());
                msgSb.append(StringConstants.NEWLINE + Messages.Action);
                msgSb.append(StringConstants.COLON + StringConstants.SPACE);
                msgSb.append(CompSystemI18n.getString(
                        xmlCap.getActionName(), true));
                msgSb.append(StringConstants.NEWLINE + Messages.Parameter);
                msgSb.append(StringConstants.COLON + StringConstants.SPACE);
                msgSb.append(CompSystemI18n.getString(
                        parDescList.get(tdRow).getName(), true));
                final String msg = msgSb.toString();
                log.error(msg, ioobe);
                continue;
            }
            int tdCell = 0;
           
            for (TestDataCell cellXml : rowXml.getDataList()) {
                String uniqueId = parDescList.get(tdCell).getUniqueId();
                final int ownerIndex = owner.getDataManager()
                    .findColumnForParam(uniqueId);
                if (ownerIndex > -1) {
                    // only relevant for old projects
                    tdList.set(ownerIndex, 
                            PoMaker.createTestDataPO(readData(cellXml, owner)));
                }
                tdCell++;
            }
            // We need to clear the data manager first because a Test Step
            // can only have one Data Set, and we want to insert the Data Set
            // as a single unit (rather than updating each cell individually)
            tdman.clear();

            tdman.insertDataSet(PoMaker.createListWrapperPO(tdList), tdRow);
            tdRow++;
        }
        return tdman;
    }

    /**
     * @param cellXml associated cell from import
     * @param owner The owner of the data.
     * @return the map read from the provided data.
     */
    private Map<Locale, String> readData(TestDataCell cellXml, 
            IParameterInterfacePO owner) {
        
        Map<Locale, String> localeToValue = new HashMap<Locale, String>(); 
        for (I18NString i18nVal : cellXml.getDataList()) {
            if (i18nVal != null && !i18nVal.isNil()
                && i18nVal.getValue() != null
                && !(i18nVal.getValue().length() == 0)) {
                String i18nValString = i18nVal.getValue();
                
                try {
                    // Since we are not using the converter for anything other than
                    // parsing, we can use null for paramDesc
                    ModelParamValueConverter converter = 
                        new ModelParamValueConverter(
                            i18nValString, owner, 
                            LocaleUtil.convertStrToLocale(
                                i18nVal.getLanguage()), 
                            null);

                    if (!converter.containsErrors()) {
                        // Only try to replace reference GUIDs if the 
                        // string could be successfully parsed.
                        // Otherwise, the model string will be overwritten with
                        // the empty string because no tokens were created 
                        // during parsing. 
                        converter.replaceGuidsInReferences(m_oldToNewGuids);
                    } else {
                        m_unparseableParameters.add(i18nValString);
                    }

                    i18nValString = converter.getModelString();
                } catch (IllegalArgumentException iae) {
                    // Do nothing.
                    // The i18nValue uses the old format and can therefore
                    // not be parsed. This value will be converted in V1M42Converter.
                }
                localeToValue.put(
                        LocaleUtil.convertStrToLocale(i18nVal.getLanguage()), 
                        i18nValString);
            } 
        }
        
        return localeToValue;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans).
     * @param assignNewGuids <code>true</code> if the parameters were given
     *        new unique IDs. Otherwise <code>false</code>.
     * @param mapper Mapper to resolve param names.
     * @return a persistent object generated from the information in the XML
     *         element
     */
    private ITestDataCategoryPO createTestDataCategory(TestDataCategory xml, 
            boolean assignNewGuids, IParamNameMapper mapper) {
        
        ITestDataCategoryPO testDataCategory = 
                PoMaker.createTestDataCategoryPO(xml.getName());
        
        for (TestDataCategory subCategory : xml.getTestDataCategoryList()) {
            testDataCategory.addCategory(createTestDataCategory(
                    subCategory, assignNewGuids, mapper));
        }
        
        for (NamedTestData testData : xml.getNamedTestDataList()) {
            testDataCategory.addTestData(
                    createTestDataCube(testData, assignNewGuids, mapper));
        }

        return testDataCategory;
    }

    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans).
     * @param assignNewGuids <code>true</code> if the parameters were given
     *        new unique IDs. Otherwise <code>false</code>.
     * @param mapper Mapper to resolve param names.
     * @return a persistent object generated from the information in the XML
     *         element
     */
    private ITestDataCubePO createTestDataCube(NamedTestData xml,
            boolean assignNewGuids, IParamNameMapper mapper) {

        ITestDataCubePO testDataCube = 
            PoMaker.createTestDataCubePO(xml.getName());
        for (ParamDescription xmlParamDesc 
                : xml.getParameterDescriptionList()) {
            if (assignNewGuids) {
                IParamDescriptionPO paramDesc = 
                    testDataCube.addParameter(xmlParamDesc.getType(), 
                            xmlParamDesc.getName(), mapper);
                m_oldToNewGuids.put(xmlParamDesc.getUniqueId(), 
                        paramDesc.getUniqueId());
            } else {
                testDataCube.addParameter(xmlParamDesc.getType(), 
                        xmlParamDesc.getName(), xmlParamDesc.getUniqueId(),
                        mapper);
            }
        }
        testDataCube.setDataManager(createTDManager(testDataCube,
                xml.getTestData(), assignNewGuids));
        return testDataCube;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param owner The ParamNode which holds this TDManager
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * the test data, otherwise a new TDManager is created.
     * @return a persistent object generated from the information in the XML
     * element
     * @param assignNewGuids <code>true</code> if the parameters were given
     *        new unique IDs. Otherwise <code>false</code>.
     */
    private ITDManager createTDManager(IParameterInterfacePO owner, 
        TestData xml, boolean assignNewGuids) {

        List<String> uniqueIds = new ArrayList<String>(
            xml.getUniqueIdsList());
        final ITDManager tdman;

        if (assignNewGuids) {
            // Update list of unique IDs
            List<String> newUniqueIds = new ArrayList<String>();
            for (String id : uniqueIds) {
                if (Pattern.matches(
                    "[0-9a-fA-F]{" + GUID_LENGTH + "}", id) //$NON-NLS-1$ //$NON-NLS-2$
                        && m_oldToNewGuids.containsKey(id)) {
                    // Use new GUID
                    newUniqueIds.add(m_oldToNewGuids.get(id));
                } else {
                    // Leave as-is
                    newUniqueIds.add(id);
                }
            }

            uniqueIds = newUniqueIds;
        }

        if (uniqueIds.isEmpty()) {
            tdman = PoMaker.createTDManagerPO(owner);        
        } else {
            tdman = PoMaker.createTDManagerPO(owner, uniqueIds);
        }
        for (TestDataRow rowXml : xml.getRowList()) {
            final List<ITestDataPO> td = new ArrayList<ITestDataPO>(rowXml
                .sizeOfDataArray());
            for (TestDataCell cellXml : rowXml.getDataList()) {
                td.add(PoMaker.createTestDataPO(readData(cellXml, owner)));
            }
            tdman.insertDataSet(PoMaker.createListWrapperPO(td), 
                    tdman.getDataSetCount());
        }
        return tdman;
    }
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the testcase 
     *                      should be assigned a new GUID. Otherwise 
     *                      <code>false</code>.
     * @param mapper mapper to resolve param names
     * @return a persistent object generated from the information in the XML
     * element
     */
    private ISpecTestCasePO createTestCaseBase(IProjectPO proj,
        TestCase xml, boolean assignNewGuid, IParamNameMapper mapper) {
        ISpecTestCasePO tc;
        
        if (xml.getId() != null) {
            tc = NodeMaker.createSpecTestCasePO(xml.getName());
            m_tcRef.put(xml.getId(), tc);
        } else if (assignNewGuid) {
            tc = NodeMaker.createSpecTestCasePO(xml.getName());
            m_tcRef.put(tc.getGuid(), tc);
            m_oldToNewGuids.put(xml.getGUID(), tc.getGuid());
        } else {
            tc = NodeMaker.createSpecTestCasePO(xml.getName(), xml.getGUID());
            m_tcRef.put(xml.getGUID(), tc);
        }
        
        tc.setComment(xml.getComment());
        tc.setDescription(xml.getDescription());
        tc.setGenerated(xml.getGenerated());
        tc.setTaskId(xml.getTaskId());
        tc.setInterfaceLocked(xml.getInterfaceLocked());
        tc.setDataFile(xml.getDatafile());
        fillTrackedChangesInformation(tc, xml);
        
        if (xml.getReferencedTestData() != null) {
            String referencedDataName = xml.getReferencedTestData();
            for (IParameterInterfacePO testDataCube 
                    : TestDataCubeBP.getAllTestDataCubesFor(proj)) {
                if (referencedDataName.equals(testDataCube.getName())) {
                    tc.setReferencedDataCube(testDataCube);
                    break;
                }
            }
        }
        for (ParamDescription pdXml : xml.getParameterDescriptionList()) {
            String uniqueId = pdXml.getUniqueId();

            if (assignNewGuid) {
                IParamDescriptionPO paramDesc = 
                    tc.addParameter(pdXml.getType(), pdXml.getName(), mapper);
                m_oldToNewGuids.put(uniqueId, paramDesc.getUniqueId());
            } else {
                if (uniqueId != null
                    && Pattern.matches(
                        "[0-9a-fA-F]{" + GUID_LENGTH + "}", uniqueId)) { //$NON-NLS-1$ //$NON-NLS-2$
                    // use the existent guid for parameter
                    tc.addParameter(pdXml.getType(), pdXml.getName(), uniqueId,
                        mapper);
                } else {
                    // creates a new GUID for parameter (only for conversion of
                    // old projects)
                    tc.addParameter(pdXml.getType(), pdXml.getName(), mapper);
                }
            }
        }
        tc.setDataManager(
                createTDManager(tc, xml.getTestdata(), assignNewGuid));
        return tc;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the test suite
     *                      should be assigned a new GUID. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     */

    private ITestSuitePO createTestSuite(IProjectPO proj, TestSuite xml, 
        boolean assignNewGuid) {
        ITestSuitePO ts;

        if (xml.getGUID() != null && !assignNewGuid) {
            ts = NodeMaker.createTestSuitePO(xml.getName(), xml.getGUID());
        } else {
            ts = NodeMaker.createTestSuitePO(xml.getName());
        }
        
        if (assignNewGuid) {
            m_oldToNewGuids.put(xml.getGUID(), ts.getGuid());
        }
        
        ts.setComment(xml.getComment());
        ts.setDescription(xml.getDescription());
        ts.setTaskId(xml.getTaskId());
        
        fillTrackedChangesInformation(ts, xml);
        
        ts.setCmdLineParameter(xml.getCommandLineParameter());
        if (xml.getSelectedAut() != null) {
            ts.setAut(findReferencedAut(xml.getSelectedAut()));
        }
        for (RefTestCase refXml : xml.getUsedTestcaseList()) {
            ts.addNode(createExecTestCase(proj, refXml, assignNewGuid));
        }
        
        Map<String, Integer> defaultEventHandler = 
            new HashMap<String, Integer>();
        for (EventHandler evhXml : xml.getEventHandlerList()) {
            defaultEventHandler.put(evhXml.getEvent(), evhXml
                .getReentryProperty().intValue());
            // Trac#1908 no place to store the max. number of retries
        }
        ts.setDefaultEventHandler(defaultEventHandler);
        ts.setStepDelay(xml.getStepDelay());
        if (!xml.isSetRelevant()) {
            ts.setRelevant(true);
        } else {
            ts.setRelevant(xml.getRelevant());
        }
        return ts;
    }
    
    /**
     * Creates the instance of the persistent object which is defined by the
     * XML element used as prameter. The method generates all dependend objects
     * as well.
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the test suite
     *                      should be assigned a new GUID. Otherwise 
     *                      <code>false</code>.
     * @return a persistent object generated from the information in the XML
     * element
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     */
    private ITestJobPO createTestJob(TestJobs xml, boolean assignNewGuid) 
        throws InvalidDataException {
        ITestJobPO tj;
        if (xml.getGUID() != null && !assignNewGuid) {
            tj = NodeMaker.createTestJobPO(xml.getName(), xml.getGUID());
        } else {
            tj = NodeMaker.createTestJobPO(xml.getName());
        }
        tj.setComment(xml.getComment());
        tj.setDescription(xml.getDescription());
        tj.setTaskId(xml.getTaskId());
        fillTrackedChangesInformation(tj, xml);
        
        for (RefTestSuite xmlRts : xml.getRefTestSuiteList()) {
            IRefTestSuitePO rts;
            if (assignNewGuid) {
                // Only Test Suites from the same project can be referenced,
                // and all Test Suites for this Project have already been 
                // initialized (so they have already been entered into the 
                // old to new GUID map). This is why we can simply directly use 
                // the old to new GUID map.
                String testSuiteGuid = m_oldToNewGuids.get(xmlRts.getTsGuid());
                if (testSuiteGuid == null) {
                    throw new InvalidDataException(
                            "Test Suite Reference: No new GUID found for Test Suite with old GUID: " + xmlRts.getTsGuid(),  //$NON-NLS-1$ 
                            MessageIDs.E_IMPORT_PROJECT_XML_FAILED);
                }
                rts = NodeMaker.createRefTestSuitePO(xmlRts.getName(), 
                        testSuiteGuid, xmlRts.getAutId());
            } else {
                rts = NodeMaker.createRefTestSuitePO(xmlRts.getName(), xmlRts
                        .getGUID(), xmlRts.getTsGuid(), xmlRts.getAutId());

            }
            rts.setComment(xmlRts.getComment());
            rts.setDescription(xmlRts.getDescription());
            tj.addNode(rts);
        }
        return tj;
    }

    /**
     * @param poNode
     *            the persistent object to fill
     * @param xmlNode
     *            the xml node to read from
     */
    private void fillTrackedChangesInformation(INodePO poNode, Node xmlNode) {
        List<MapEntry> trackedModificationList = 
                xmlNode.getTrackedModificationList();
        if (!trackedModificationList.isEmpty() && !m_skipTrackingInformation) {
            Map<Long, String> trackedChanges = new HashMap<Long, String>();
            for (MapEntry me : trackedModificationList) {
                trackedChanges.put(Long.valueOf(me.getKey()), me.getValue());
            }
            poNode.setTrackedChangesMap(trackedChanges);
        }
    }

    /**
     * Shared method for setting values into ExecTCs and their subclasses.
     * 
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param exec TC to be initialized
     * @param assignNewGuid <code>true</code> if nodes are being assigned
     *                      new GUIDs. Otherwise <code>false</code>.
     */
    private void fillExecTestCase(IProjectPO proj, RefTestCase xml,
        IExecTestCasePO exec, boolean assignNewGuid) {
        
        exec.setName(xml.getName());
        exec.setComment(xml.getComment());
        exec.setDescription(xml.getDescription());
        exec.setGenerated(xml.getGenerated());
        exec.setTaskId(xml.getTaskId());
        if (xml.isSetActive()) {
            exec.setActive(xml.getActive());
        } else {
            exec.setActive(true);
        }
        exec.setDataFile(xml.getDatafile());
        if (xml.getReferencedTestData() != null) {
            String referencedDataName = xml.getReferencedTestData();
            for (IParameterInterfacePO testDataCube 
                    : TestDataCubeBP.getAllTestDataCubesFor(proj)) {
                if (referencedDataName.equals(testDataCube.getName())) {
                    exec.setReferencedDataCube(testDataCube);
                    break;
                }
            }
        }

        if (xml.getHasOwnTestdata()) {
            // ExecTestCasePO doesn't have an own parameter list.
            // It uses generally the parameter from the associated
            // SpecTestCase.
            exec.setDataManager(createTDManager(exec, xml.getTestdata(),
                    assignNewGuid));
        }
        for (CompNames overriddenXml : xml.getOverriddenNamesList()) {
            final ICompNamesPairPO compName = PoMaker.createCompNamesPairPO(
                    overriddenXml.getOriginalName(),
                    overriddenXml.getNewName(), null);
            compName.setPropagated(overriddenXml.getPropagated());
            exec.addCompNamesPair(compName);
        }
        m_monitor.worked(1);
    }

    /**
     * Find a persistent object which has an XML id.
     * 
     * @param selectedAut The XML id used to identify this instance
     * @return the object build while reading the XML element
     */
    private IAUTMainPO findReferencedAut(String selectedAut) {
        return m_autRef.get(selectedAut);
    }
    
    /**
     * Find a persistent object which has an XML id.
     * 
     * @param usedTestcase The XML id used to identify this instance
     * @return the object build while reading the XML element, or 
     *         <code>null</code> if the object cannot be found
     */
    private ISpecTestCasePO findReferencedTC(String usedTestcase) {        
        return m_tcRef.get(usedTestcase);
    }
    /**
     * Find a persistent object which has a GUID.
     * 
     * @param usedTestcaseGuid The GUID used to identify this instance
     * @param projectGuid The GUID of the spec testcase's parent project
     * @param parentProject The parent project of the exec testcase
     * @param assignNewGuid <code>true</code> if elements are being assigned new 
     *                      GUIDs. Otherwise <code>false</code>.
     * @return the object build while reading the XML element, or 
     *         <code>null</code> if the object cannot be found
     */
    private ISpecTestCasePO findReferencedTCByGuid(String usedTestcaseGuid, 
        String projectGuid, IProjectPO parentProject, boolean assignNewGuid) {
        String actualProjectGuid = assignNewGuid 
            ? m_oldToNewGuids.get(projectGuid) : projectGuid;
        if (projectGuid == null
            || parentProject.getGuid().equals(actualProjectGuid)) {
            // Referenced TC is in same project
            if (assignNewGuid) {
                return m_tcRef.get(m_oldToNewGuids.get(usedTestcaseGuid));
            }
            return m_tcRef.get(usedTestcaseGuid);
        }
        
        // Referenced TC is in different project
        return NodePM.getSpecTestCase(parentProject.getUsedProjects(), 
            projectGuid, usedTestcaseGuid);
    }
    /**
     * This is the second run on categories. The first time the categories were
     * created and the contained TestCases were initialized. In this run all
     * TestCases will be completed.
     * @param proj The IProjectPO which is currently build. The instance is
     * needed by some objects to verify that their data confirms to project
     * specification (for instance languages).
     * @param xml Abstraction of the XML element (see Apache XML Beans)
     * @param assignNewGuid <code>true</code> if the category and all subnodes
     *                      should be assigned new GUIDs. Otherwise 
     *                      <code>false</code>.
     * @param attrDescSession The session used for locating attribute 
     *                        descriptions in the database.
     * @throws InvalidDataException if some data is invalid when constructing
     * an object. This should not happen for exported project, but may happen
     * when someone generates XML project description outside of GUIdancer.
     * @throws InterruptedException if the operation was canceled.
     */
    private void rerunCategories(IProjectPO proj, Category xml, 
        boolean assignNewGuid, EntityManager attrDescSession) 
        throws InvalidDataException, InterruptedException {
        
        for (Category catXml  : xml.getCategoryList()) {
            checkCancel();
            rerunCategories(proj, catXml, assignNewGuid, attrDescSession);
        }
        
        for (TestCase tcXml : xml.getTestcaseList()) {
            checkCancel();
            completeTestCase(proj, tcXml, assignNewGuid, attrDescSession);
        }
    }
    
    /**
     * 
     * @param componentType
     *            component type name
     * @return true if the component has a default mapping and therefore has no
     *         component name
     */
    private boolean componentHasDefaultMapping(String componentType) {
        Component component = ComponentBuilder.getInstance()
                .getCompSystem().findComponent(componentType);
        if (component.isConcrete()) {
            return ((ConcreteComponent)component).hasDefaultMapping();
        }        
        return false;
    }

}
