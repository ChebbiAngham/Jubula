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
package org.eclipse.jubula.client.ui.rcp.editors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jubula.client.core.businessprocess.CompNameResult;
import org.eclipse.jubula.client.core.businessprocess.CompNamesBP;
import org.eclipse.jubula.client.core.businessprocess.ComponentNamesBP;
import org.eclipse.jubula.client.core.businessprocess.IComponentNameCache;
import org.eclipse.jubula.client.core.businessprocess.IObjectMappingObserver;
import org.eclipse.jubula.client.core.businessprocess.IWritableComponentNameCache;
import org.eclipse.jubula.client.core.businessprocess.IWritableComponentNameMapper;
import org.eclipse.jubula.client.core.businessprocess.ObjectMappingComponentNameMapper;
import org.eclipse.jubula.client.core.businessprocess.ObjectMappingEventDispatcher;
import org.eclipse.jubula.client.core.businessprocess.TestExecution;
import org.eclipse.jubula.client.core.businessprocess.db.TestSuiteBP;
import org.eclipse.jubula.client.core.businessprocess.db.TimestampBP;
import org.eclipse.jubula.client.core.commands.AUTModeChangedCommand;
import org.eclipse.jubula.client.core.events.DataChangedEvent;
import org.eclipse.jubula.client.core.events.DataEventDispatcher;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.DataState;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.OMState;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.UpdateState;
import org.eclipse.jubula.client.core.model.IAUTMainPO;
import org.eclipse.jubula.client.core.model.ICapPO;
import org.eclipse.jubula.client.core.model.IComponentNamePO;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IObjectMappingAssoziationPO;
import org.eclipse.jubula.client.core.model.IObjectMappingCategoryPO;
import org.eclipse.jubula.client.core.model.IObjectMappingPO;
import org.eclipse.jubula.client.core.model.IObjectMappingProfilePO;
import org.eclipse.jubula.client.core.model.IPersistentObject;
import org.eclipse.jubula.client.core.model.ITestSuitePO;
import org.eclipse.jubula.client.core.model.ITimestampPO;
import org.eclipse.jubula.client.core.model.PoMaker;
import org.eclipse.jubula.client.core.persistence.CompNamePM;
import org.eclipse.jubula.client.core.persistence.EditSupport;
import org.eclipse.jubula.client.core.persistence.GeneralStorage;
import org.eclipse.jubula.client.core.persistence.IncompatibleTypeException;
import org.eclipse.jubula.client.core.persistence.PMAlreadyLockedException;
import org.eclipse.jubula.client.core.persistence.PMException;
import org.eclipse.jubula.client.core.utils.AbstractNonPostOperatingTreeNodeOperation;
import org.eclipse.jubula.client.core.utils.ITreeTraverserContext;
import org.eclipse.jubula.client.core.utils.TreeTraverser;
import org.eclipse.jubula.client.ui.constants.ContextHelpIds;
import org.eclipse.jubula.client.ui.constants.IconConstants;
import org.eclipse.jubula.client.ui.rcp.Plugin;
import org.eclipse.jubula.client.ui.rcp.businessprocess.CompletenessBP;
import org.eclipse.jubula.client.ui.rcp.businessprocess.OMEditorBP;
import org.eclipse.jubula.client.ui.rcp.constants.RCPCommandIDs;
import org.eclipse.jubula.client.ui.rcp.controllers.ComponentNameTreeViewerUpdater;
import org.eclipse.jubula.client.ui.rcp.controllers.OpenOMETracker;
import org.eclipse.jubula.client.ui.rcp.controllers.PMExceptionHandler;
import org.eclipse.jubula.client.ui.rcp.controllers.TestExecutionContributor;
import org.eclipse.jubula.client.ui.rcp.controllers.dnd.objectmapping.LimitingDragSourceListener;
import org.eclipse.jubula.client.ui.rcp.controllers.dnd.objectmapping.OMDropTargetListener;
import org.eclipse.jubula.client.ui.rcp.dialogs.NagDialog;
import org.eclipse.jubula.client.ui.rcp.editors.JBEditorHelper.EditableState;
import org.eclipse.jubula.client.ui.rcp.events.GuiEventDispatcher;
import org.eclipse.jubula.client.ui.rcp.events.GuiEventDispatcher.IEditorDirtyStateListener;
import org.eclipse.jubula.client.ui.rcp.filter.JBFilteredTree;
import org.eclipse.jubula.client.ui.rcp.filter.ObjectMappingEditorPatternFilter;
import org.eclipse.jubula.client.ui.rcp.i18n.Messages;
import org.eclipse.jubula.client.ui.rcp.propertytester.EditorPartPropertyTester;
import org.eclipse.jubula.client.ui.rcp.provider.contentprovider.objectmapping.OMEditorTreeContentProvider;
import org.eclipse.jubula.client.ui.rcp.provider.labelprovider.OMEditorTreeLabelProvider;
import org.eclipse.jubula.client.ui.rcp.provider.selectionprovider.SelectionProviderIntermediate;
import org.eclipse.jubula.client.ui.utils.CommandHelper;
import org.eclipse.jubula.client.ui.utils.DialogUtils;
import org.eclipse.jubula.client.ui.utils.ErrorHandlingUtil;
import org.eclipse.jubula.client.ui.utils.LayoutUtil;
import org.eclipse.jubula.client.ui.views.IJBPart;
import org.eclipse.jubula.client.ui.views.IMultiTreeViewerContainer;
import org.eclipse.jubula.communication.internal.message.ChangeAUTModeMessage;
import org.eclipse.jubula.tools.internal.constants.StringConstants;
import org.eclipse.jubula.tools.internal.exception.ProjectDeletedException;
import org.eclipse.jubula.tools.internal.i18n.I18n;
import org.eclipse.jubula.tools.internal.objects.IComponentIdentifier;
import org.eclipse.jubula.tools.internal.xml.businessmodell.ConcreteComponent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPartConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.services.IEvaluationService;
import org.eclipse.ui.swt.IFocusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Editor for managing Object Mapping in Jubula.
 *
 * @author BREDEX GmbH
 * @created Oct 21, 2008
 */
public class ObjectMappingMultiPageEditor extends MultiPageEditorPart 
    implements IJBPart, IJBEditor, IObjectMappingObserver, 
               IEditorDirtyStateListener, IMultiTreeViewerContainer, 
               IPropertyListener {
    /** Show-menu */
    public static final String CLEANUP_ID = PlatformUI.PLUGIN_ID + ".CleanupSubMenu"; //$NON-NLS-1$
    
    /** default sash weights */
    private static final int[] DEFAULT_SASH_WEIGHTS = new int[] { 25, 100 };

    /** the logger */
    private static final Logger LOG = 
        LoggerFactory.getLogger(ObjectMappingMultiPageEditor.class);
    
    /** page index of the split view */
    private static final int SPLIT_PAGE_IDX = 0;

    /** the object responsible for handling JBEditor-related tasks */
    private JBEditorHelper m_editorHelper;
    
    /** handles the business process operations for this editor */
    private OMEditorBP m_omEditorBP;
    
    /** the tree viewer for unmapped Component Names in the Split Pane view */
    private TreeViewer m_compNameTreeViewer;
    
    /** the tree viewer for unmapped UI Elements in the Split Pane view */
    private TreeViewer m_uiElementTreeViewer;
    
    /** the tree viewer for mapped Component Names in the Split Pane view */
    private TreeViewer m_mappedComponentTreeViewer;
    
    /** 
     * the component responsible for handling the profile 
     * configuration page 
     */
    private ObjectMappingConfigComponent m_mappingConfigComponent;
    
    /** updater for tree viewer based on changes to Component Names */
    private ComponentNameTreeViewerUpdater m_treeViewerUpdater;

    /** mapping: page number => selection provider for that page */
    private Map<Integer, ISelectionProvider> m_pageToSelectionProvider =
        new HashMap<Integer, ISelectionProvider>();
    
    /**
     * Always provides an empty selection. Does not track selection listeners.
     *
     * @author BREDEX GmbH
     * @created Jan 20, 2009
     */
    private static class NullSelectionProvider implements ISelectionProvider {

        /**
         * {@inheritDoc}
         */
        public void addSelectionChangedListener(
                ISelectionChangedListener listener) {

            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        public ISelection getSelection() {
            return StructuredSelection.EMPTY;
        }

        /**
         * {@inheritDoc}
         */
        public void removeSelectionChangedListener(
                ISelectionChangedListener listener) {

            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        public void setSelection(ISelection selection) {
            // Do nothing.
        }
        
    }
    
    /**
     * Sorter for the Object Mapping Editor's tree view.
     *
     * @author BREDEX GmbH
     * @created Mar 10, 2009
     */
    private static class ObjectMappingTreeSorter extends ViewerSorter {
        /**
         * {@inheritDoc}
         */
        public int category(Object element) {
            if (element instanceof IObjectMappingCategoryPO) {
                return 0;
            } else if (element instanceof IObjectMappingAssoziationPO) {
                return 1;
            } else if (element instanceof IComponentNamePO) {
                return 2;
            }
            return super.category(element);
        }
    }
    
    /**
     * This class operates on any node of the test suite tree to extract the
     * component names. They are added to the object mapping tree and stored in
     * the member <code>m_componentNames</code>.
     */
    private class CollectLogicalNamesOp 
        extends AbstractNonPostOperatingTreeNodeOperation<INodePO> {
        /** Number of added component names (nodes). */
        private int m_addedNodeCount = 0;
        /** list of added GuiNodes */
        private List<IObjectMappingAssoziationPO> m_addedNodes = 
            new ArrayList<IObjectMappingAssoziationPO>();
        /** The business process that performs component name operations */
        private CompNamesBP m_compNamesBP = new CompNamesBP();
        
        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public boolean operate(ITreeTraverserContext<INodePO> ctx, 
                INodePO parent, INodePO node, boolean alreadyVisited) {
            if (node instanceof ICapPO) {
                final ICapPO cap = (ICapPO)node;
                CompNameResult result = 
                    m_compNamesBP.findCompName(ctx.getCurrentTreePath(), 
                            cap, cap.getComponentName(),
                            getCompMapper().getCompNameCache());
                final IComponentNamePO compNamePo = 
                    getCompMapper().getCompNameCache().getCompNamePo(
                            result.getCompName());
                if (compNamePo != null) {
                    if (!(cap.getMetaComponentType() 
                                instanceof ConcreteComponent
                            && ((ConcreteComponent)cap.getMetaComponentType())
                                .hasDefaultMapping())
                            && m_omEditorBP.getAssociation(
                                    compNamePo.getGuid()) == null) {
                        if (getEditorHelper().requestEditableState() 
                                != EditableState.OK) {
                            return true;
                        }

                        IObjectMappingAssoziationPO assoc = 
                            PoMaker.createObjectMappingAssoziationPO(
                                    null, new HashSet<String>());
                        try {
                            getCompMapper().changeReuse(
                                    assoc, null, compNamePo.getGuid());
                            getAut().getObjMap().getUnmappedLogicalCategory()
                                .addAssociation(assoc);
                            m_addedNodes.add(assoc);
                            m_addedNodeCount++;
                        } catch (IncompatibleTypeException e) {
                            ErrorHandlingUtil.createMessageDialog(
                                    e, e.getErrorMessageParams(), null);
                        } catch (PMException pme) {
                            // Should not happen since we are assigning the
                            // Component Name to an unmapped association.
                            // Log it just in case.
                            LOG.error(Messages.ErrorCollectingComponentNames
                                    + StringConstants.DOT, pme);
                        }
                    }
                }
            }
            return true;
        }
        /**
         * @return Returns the addedNodeCount.
         */
        public int getAddedNodeCount() {
            return m_addedNodeCount;
        }
        
        /**
         * @return Returns the addedNodeCount.
         */
        public List<IObjectMappingAssoziationPO> getAddedNodes() {
            return m_addedNodes;
        }
    }
    
    /** the selection provider for this editor */
    private SelectionProviderIntermediate m_selectionProvider;
    
    /** the active tree viewer */
    private TreeViewer m_activeTreeViewer = null;
    
    /**
     * <code>m_treeFilterText</code>tree Viewer
     */
    private Text m_treeFilterText;

    /** selection provider for the split pane view */
    private SelectionProviderIntermediate m_splitPaneSelectionProvider;
    
    /** component mapper */
    private IWritableComponentNameMapper m_compMapper; 
    
    /**
     * {@inheritDoc}
     */
    protected void createPages() {
        if (m_editorHelper == null) {
            m_editorHelper = new JBEditorHelper(this);
        }
        m_omEditorBP = new OMEditorBP(this);
        IObjectMappingPO objMap = getAut().getObjMap();
        if (objMap == null) {
            objMap = PoMaker.createObjectMappingPO();
            getAut().setObjMap(objMap);
        }
        
        checkMasterSessionUpToDate();
        
        m_compMapper = 
                getEditorHelper().getEditSupport()
                    .getCompMapper();

        // Create menu manager.
        MenuManager menuMgr = createContextMenu();

        GuiEventDispatcher.getInstance().addEditorDirtyStateListener(
                this, true);
        getEditorHelper().addListeners();
        getOmEditorBP().collectNewLogicalComponentNames();
        
        ObjectMappingComponentNameMapper mapper;
        mapper =  (ObjectMappingComponentNameMapper) getEditorHelper()
                .getEditSupport().getCompMapper();
        mapper.initCompNameCache(getAut());
        
        int splitPaneViewIndex = addPage(
                createSplitPanePageControl(getContainer(), menuMgr));
        int configViewIndex = addPage(createConfigPageControl(getContainer()));
      
        setPageText(
                splitPaneViewIndex, 
                Messages.ObjectMappingEditorSplitPaneView);
        setPageText(
                configViewIndex, 
                Messages.ObjectMappingEditorConfigView);
        
        m_pageToSelectionProvider.put(splitPaneViewIndex, 
                m_splitPaneSelectionProvider);
        m_pageToSelectionProvider.put(
                configViewIndex, new NullSelectionProvider());
        
        m_selectionProvider = new SelectionProviderIntermediate();
        m_selectionProvider.setSelectionProviderDelegate(
                m_pageToSelectionProvider.get(getActivePage()));
        getSite().setSelectionProvider(m_selectionProvider);
        getEditorSite().registerContextMenu(menuMgr, m_selectionProvider);
        
        ObjectMappingEventDispatcher.addObserver(this);
        checkAndFixInconsistentData();
        OpenOMETracker.INSTANCE.addOME(this);
    }

    /**
     * @return the context menu manager
     */
    private MenuManager createContextMenu() {
        MenuManager menuMgr = new MenuManager();
        menuMgr.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
        return menuMgr;
    }

    /**
     * Checks whether data from the editor input is inconsistent and fixes any
     * inconsistencies, saving immediately afterward if necessary.
     */
    private void checkAndFixInconsistentData() {
        
        boolean isChanged = false;
        
        IObjectMappingPO objMap = getAut().getObjMap();

        
        isChanged |= fixCompNameReferences(objMap,
                m_compMapper.getCompNameCache());
        isChanged |= removeDeletedCompNames(objMap,
                m_compMapper.getCompNameCache());
        
        
        if (isChanged) {
            try {
                final EditSupport editSupport = m_editorHelper.getEditSupport();
                editSupport.lockWorkVersion();
                m_editorHelper.setDirty(true);
                doSave(new NullProgressMonitor());
            } catch (PMAlreadyLockedException e) {
                // ignore, we are only doing housekeeping
            } catch (PMException e) {
                PMExceptionHandler.handlePMExceptionForMasterSession(e);
            }
        }
    }

    /**
     * Removes deleted Component Names and empty associations from the given
     * Object Map.
     * 
     * @param objectMap The Object Map to fix.
     * @param compNameCache The cache to use for retrieving Component Names.
     * 
     * @return <code>true</code> if this method call caused any change
     *         (i.e. if any Component Names were removed). 
     *         Otherwise, <code>false</code>.
     */
    private boolean removeDeletedCompNames(
            IObjectMappingPO objectMap, 
            IComponentNameCache compNameCache) {

        boolean isChanged = false;

        Set<IObjectMappingAssoziationPO> assocsToDelete = 
            new HashSet<IObjectMappingAssoziationPO>();
        
        for (IObjectMappingAssoziationPO assoc : objectMap.getMappings()) {
            if (assoc.getTechnicalName() == null) {
                Set<String> compNamesToRemove = new HashSet<String>();
                for (String compNameGuid : assoc.getLogicalNames()) {
                    if (compNameCache.getCompNamePo(compNameGuid) == null) {
                        compNamesToRemove.add(compNameGuid);
                    }
                }
                for (String toRemove : compNamesToRemove) {
                    assoc.removeLogicalName(toRemove);
                    isChanged = true;
                }
                if (assoc.getLogicalNames().isEmpty()) {
                    isChanged = true;
                    assocsToDelete.add(assoc);
                }
            }
        }
        for (IObjectMappingAssoziationPO assoc : assocsToDelete) {
            assoc.getCategory().removeAssociation(assoc);
            getEditorHelper().getEditSupport().getSession()
                .remove(assoc);
        }

        return isChanged;
    }

    /**
     * Creates the profile configuration page of the editor.
     * 
     * @param parent The parent composite.
     * @return the base control of the profile configuration page.
     */
    private Control createConfigPageControl(Composite parent) {
     
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        layout.verticalSpacing = 3;
        layout.marginWidth = LayoutUtil.MARGIN_WIDTH;
        layout.marginHeight = LayoutUtil.MARGIN_HEIGHT;
        parent.setLayout(layout);
        Composite configComposite = new Composite(parent, SWT.NONE);
        GridData gridData = 
            new GridData (GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_BOTH);
        configComposite.setLayoutData(gridData);

        layout = new GridLayout();
        layout.numColumns = 1;
        layout.verticalSpacing = 3;
        layout.marginWidth = LayoutUtil.MARGIN_WIDTH;
        layout.marginHeight = LayoutUtil.MARGIN_HEIGHT;
        configComposite.setLayout(layout);
        
        m_mappingConfigComponent = new ObjectMappingConfigComponent(
                configComposite, getAut().getObjMap(), this);
        
        createConfigContextMenu(configComposite);
        
        return configComposite;
    }
    
    /**
     * Creates the split pane page of the editor.
     * 
     * @param parent The parent composite.
     * @return the base control of the split pane view.
     * @param contextMenuMgr The manager for the context menu for the created
     *                       trees.
     */
    private Control createSplitPanePageControl(Composite parent,
            MenuManager contextMenuMgr) {
        
        m_splitPaneSelectionProvider = new SelectionProviderIntermediate();
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        layout.verticalSpacing = 1;
        layout.marginWidth = 1;
        layout.marginHeight = 1;
        parent.setLayout(layout);
        SashForm mainSash = new SashForm(parent, SWT.VERTICAL);
        SashForm topSash = new SashForm(mainSash, SWT.HORIZONTAL);
    
        m_compNameTreeViewer = createSplitPaneViewer(topSash, 
                "ObjectMappingEditor.UnAssignedLogic", //$NON-NLS-1$
                getAut().getObjMap().getUnmappedLogicalCategory(),
                contextMenuMgr);

        m_splitPaneSelectionProvider.setSelectionProviderDelegate(
                m_compNameTreeViewer);
        m_uiElementTreeViewer = createSplitPaneViewer(topSash, 
                "ObjectMappingEditor.UnAssignedTech", //$NON-NLS-1$
                getAut().getObjMap().getUnmappedTechnicalCategory(),
                contextMenuMgr);
        m_mappedComponentTreeViewer = createMappedSplitPaneViewer(mainSash,
                "ObjectMappingEditor.Assigned", //$NON-NLS-1$
                getAut().getObjMap().getMappedCategory(),
                contextMenuMgr);
        
        linkSelection(new TreeViewer[] {
            m_compNameTreeViewer, m_uiElementTreeViewer, 
            m_mappedComponentTreeViewer});
        
        Plugin.getHelpSystem().setHelp(parent,
            ContextHelpIds.OBJECT_MAP_EDITOR);
        mainSash.setWeights(DEFAULT_SASH_WEIGHTS);
        return mainSash;
    }

    /**
     * "Links" the selections of the given viewers. This means that the viewer
     * selections are mutually exclusive (i.e. if something is already selected 
     * in viewer 1, and something becomes selected in viewer 2, then the 
     * selection in viewer 1 is cleared). This method also adds the given 
     * viewers to the Split Pane view's selection provider group.
     * 
     * @param treeViewersToLink The viewers to link.
     */
    private void linkSelection(final TreeViewer[] treeViewersToLink) {
        for (final TreeViewer viewer : treeViewersToLink) {
            viewer.addSelectionChangedListener(new ISelectionChangedListener() {
                public void selectionChanged(SelectionChangedEvent event) {
                    if (event.getSelection() != null 
                            && !event.getSelection().isEmpty()) {
                        
                        m_splitPaneSelectionProvider
                            .setSelectionProviderDelegate(viewer);
                        for (TreeViewer viewerToDeselect : treeViewersToLink) {
                            if (viewer != viewerToDeselect) {
                                viewerToDeselect.setSelection(
                                        StructuredSelection.EMPTY);
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Creates and returns a tree viewer suitable for use in the split pane 
     * view.
     * 
     * @param parent The parent composite for the viewer.
     * @param title the title to display for the viewer.
     * @param topLevelCategory The input for the viewer.
     * @param contextMenuMgr The manager for the context menu for the created
     *                       tree.
     * @return the created viewer.
     */
    private TreeViewer createSplitPaneViewer(
            Composite parent,
            String title,
            IObjectMappingCategoryPO topLevelCategory,
            MenuManager contextMenuMgr) {

        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout());
        Label titleLabel = new Label(composite, SWT.NONE);
        titleLabel.setText(I18n.getString(title));
        titleLabel.setLayoutData(
                GridDataFactory.defaultsFor(titleLabel).create());

        final TreeViewer viewer = new TreeViewer(composite);
        
        viewer.getTree().setLayoutData(
                GridDataFactory.fillDefaults().grab(true, true).create());
        setProviders(viewer, getCompMapper());
        viewer.setUseHashlookup(true);
        viewer.setSorter(new ObjectMappingTreeSorter());
        viewer.setComparer(new PersistentObjectComparer());
        viewer.setInput(topLevelCategory);

        Transfer[] transfers = 
            new Transfer[] { 
                org.eclipse.jface.util.LocalSelectionTransfer.getTransfer()};
        viewer.addDragSupport(DND.DROP_MOVE, transfers,
                new LimitingDragSourceListener(viewer, getAut()));
        viewer.addDropSupport(DND.DROP_MOVE, transfers, 
            new OMDropTargetListener(this, viewer));

        createTreeContextMenu(viewer, contextMenuMgr);

        DialogUtils.setWidgetName(viewer.getTree(), title);
        
        IFocusService focusService = getSite().getService(IFocusService.class);
        
        focusService.addFocusTracker(viewer.getTree(), title);
        viewer.getTree().addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                m_activeTreeViewer = viewer;
            }
        });
        
        return viewer;
    }
    
    /**
     * Creates and returns a tree viewer suitable for use in the split pane 
     * view.
     * 
     * @param parent The parent composite for the viewer.
     * @param i18nTitleKey the title to display for the viewer.
     * @param topLevelCategory The input for the viewer.
     * @param contextMenuMgr The manager for the context menu for the created
     *                       tree.
     * @return the created viewer.
     */
    private TreeViewer createMappedSplitPaneViewer(
            Composite parent,
            String i18nTitleKey,
            IObjectMappingCategoryPO topLevelCategory,
            MenuManager contextMenuMgr) {

        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout());
        
        final FilteredTree ft = new JBFilteredTree(composite, SWT.MULTI
                | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER, 
                new ObjectMappingEditorPatternFilter(), true);
        setTreeFilterText(ft.getFilterControl());
        
        final TreeViewer viewer = ft.getViewer();
        
        viewer.getTree().setLayoutData(
                GridDataFactory.fillDefaults().grab(true, true).create());
        setProviders(viewer, getCompMapper());
        viewer.setUseHashlookup(true);
        viewer.setSorter(new ObjectMappingTreeSorter());
        viewer.setComparer(new PersistentObjectComparer());
        viewer.setInput(topLevelCategory);

        Transfer[] transfers = 
            new Transfer[] { 
                org.eclipse.jface.util.LocalSelectionTransfer.getTransfer()};
        viewer.addDragSupport(DND.DROP_MOVE, transfers,
                new LimitingDragSourceListener(viewer, getAut()));
        viewer.addDropSupport(DND.DROP_MOVE, transfers, 
            new OMDropTargetListener(this, viewer));

        createTreeContextMenu(viewer, contextMenuMgr);

        DialogUtils.setWidgetName(viewer.getTree(), i18nTitleKey);
        
        IFocusService focusService = getSite().getService(IFocusService.class);
        
        focusService.addFocusTracker(viewer.getTree(), i18nTitleKey);
        viewer.getTree().addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                m_activeTreeViewer = viewer;
            }
        });
        
        return viewer;
    }
    
    /**
     * @param treeFilterText the treeFilterText to set
     */
    public void setTreeFilterText(Text treeFilterText) {
        m_treeFilterText = treeFilterText;
    }

    /**
     * @return the treeFilterText
     */
    public Text getTreeFilterText() {
        return m_treeFilterText;
    }

    /**
     * 
     * @param viewer The viewer on which to create the context menu.
     * @param menuMgr The manager for the context menu.
     */
    private void createTreeContextMenu(TreeViewer viewer, MenuManager menuMgr) {
        // Create menu.
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
    }

    /**
     * Create context menu for the configuration editor view.
     * 
     * @param configComposite The composite that holds the configuration page.
     */
    private void createConfigContextMenu(Composite configComposite) {
        // Create menu manager.
        MenuManager menuMgr = new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager mgr) {
                fillConfigContextMenu(mgr);
            }
        });
        // Create menu.
        Menu menu = menuMgr.createContextMenu(configComposite);
        setConfigContextMenu(configComposite, menu);
    }

    /**
     * Recursively sets the context menu for <code>control</code> and all of its
     * children to <code>menu</code>.
     * 
     * @param control The start point for setting the menu.
     * @param menu The menu to use.
     */
    private void setConfigContextMenu(Control control, Menu menu) {
        control.setMenu(menu);
        if (control instanceof Composite) {
            for (Control child : ((Composite)control).getChildren()) {
                setConfigContextMenu(child, menu);
            }
        }
    }
    
    /**
     * fill the tree context menu
     * 
     * @param mgr
     *            IMenuManager
     */
    protected void fillConfigContextMenu(IMenuManager mgr) {
        CommandHelper.createContributionPushItem(mgr, 
                RCPCommandIDs.REVERT_CHANGES);
    }

    /**
     * {@inheritDoc}
     */
    public void doSave(IProgressMonitor monitor) {
        monitor.beginTask(Messages.EditorsSaveEditors,
                IProgressMonitor.UNKNOWN);
        boolean errorOccured = false;
        IObjectMappingPO objMap = getAut().getObjMap();
        TimestampBP.refreshTimestamp(objMap);
        try {
            if (getEditorHelper().isDirty()) {
                EditSupport editSupport = getEditorHelper().getEditSupport();
                IObjectMappingProfilePO origProfile = 
                    ((IAUTMainPO)editSupport.getOriginal()).getObjMap()
                        .getProfile();
                IObjectMappingProfilePO workProfile = 
                    ((IAUTMainPO)editSupport.getWorkVersion()).getObjMap()
                        .getProfile();

                IWritableComponentNameCache compNameCache = 
                    editSupport.getCompMapper().getCompNameCache();
                
                Set<IComponentNamePO> renamedCompNames =  
                    new HashSet<IComponentNamePO>(
                            compNameCache.getRenamedNames());
                Set<IComponentNamePO> reuseChangedCompNames = 
                    getCompNamesWithChangedReuse(compNameCache);

                fixCompNameReferences(getAut().getObjMap(), 
                        getEditorHelper().getEditSupport()
                            .getCompMapper().getCompNameCache());
                
                editSupport.saveWorkVersion();
                
                List<DataChangedEvent> events = 
                                new ArrayList<DataChangedEvent>();
                events.addAll(getRenamedEvents(renamedCompNames));
                events.addAll(getReuseChangedEvents(reuseChangedCompNames));
                
                DataEventDispatcher.getInstance().fireDataChangedListener(
                        events.toArray(new DataChangedEvent[0]));
                
                if (getAut().equals(
                        TestExecution.getInstance().getConnectedAut())
                    && !workProfile.equals(origProfile)) {
                    
                    NagDialog.runNagDialog(
                            Plugin.getActiveWorkbenchWindowShell(),
                            "InfoNagger.ObjectMappingProfileChanged", //$NON-NLS-1$
                            ContextHelpIds.OBJECT_MAP_EDITOR); 
                }
            }
            ComponentNamesBP.getInstance().init();
        } catch (PMException e) {
            PMExceptionHandler.handlePMExceptionForEditor(e, this);
            errorOccured = true;
        } catch (ProjectDeletedException e) {
            PMExceptionHandler.handleProjectDeletedException();
            errorOccured = true;
        } catch (IncompatibleTypeException ite) {
            ErrorHandlingUtil.createMessageDialog(
                    ite, ite.getErrorMessageParams(), null);
        } finally {
            monitor.done();
            if (!errorOccured) {
                try {
                    reOpenEditor(((PersistableEditorInput)getEditorInput())
                        .getNode());
                } catch (PMException e) {
                    PMExceptionHandler.handlePMExceptionForEditor(e, this);
                }
            }
        }
    }

    /**
     * 
     * @param compNameCache The cache to use for finding Component Names.
     * @return all Component Names marked within <code>compNameCache</code> as
     *         having had their reuse changed.
     */
    private Set<IComponentNamePO> getCompNamesWithChangedReuse(
            IWritableComponentNameCache compNameCache) {
        Set<IComponentNamePO> reuseChangedCompNames = 
            new HashSet<IComponentNamePO>(); 
        for (String compNameGuid : compNameCache.getReusedNames()) {
            IComponentNamePO compName = 
                compNameCache.getCompNamePo(compNameGuid);
            if (compName != null) {
                reuseChangedCompNames.add(compName);
            }
        }
        return reuseChangedCompNames;
    }

    /**
     * Get "ReuseChanged" data changed events for each given Component Name.
     * 
     * @param reuseChangedCompNames The Component Names for which to fire 
     *                              the events.
     * @return "ReuseChanged" data changed events                        
     */
    private List<DataChangedEvent> getReuseChangedEvents(
            Set<IComponentNamePO> reuseChangedCompNames) {
        List<DataChangedEvent> events = new ArrayList<DataChangedEvent>();
        for (IComponentNamePO compName : reuseChangedCompNames) {
            events.add(new DataChangedEvent(compName, DataState.ReuseChanged,
                    UpdateState.all));
        }
        return events;
    }

    /**
     * Get "Renamed" data changed events for each given Component Name.
     * 
     * @param renamedCompNames
     *            The Component Names for which to fire the events.
     * @return "Renamed" data changed events
     */
    private List<DataChangedEvent> getRenamedEvents(
            Set<IComponentNamePO> renamedCompNames) {
        List<DataChangedEvent> events = new ArrayList<DataChangedEvent>();
        for (IComponentNamePO compName : renamedCompNames) {
            events.add(new DataChangedEvent(compName, DataState.Renamed,
                    UpdateState.all));
        }
        return events;
    }

    /**
     * Replaces Component Name references with the referenced Component Names
     * and deletes any Component Name references that are no longer used.
     * 
     * @param objectMap The Object Map to fix.
     * @param compNameCache The cache to use for retrieving Component Names.
     * 
     * @return <code>true</code> if this method call caused any change
     *         (i.e. if any references were fixed). 
     *         Otherwise, <code>false</code>.
     */
    private boolean fixCompNameReferences(
            IObjectMappingPO objectMap, 
            IComponentNameCache compNameCache) {
        boolean isChanged = false;
        // Replace all reference guids with referenced guids
        for (IObjectMappingAssoziationPO assoc 
                : objectMap.getMappings()) {
            Set<String> guidsToRemove = new HashSet<String>();
            for (String compNameGuid : assoc.getLogicalNames()) {
                IComponentNamePO compNamePo = 
                    compNameCache.getCompNamePo(compNameGuid);
                if (compNamePo != null 
                        && !compNamePo.getGuid().equals(compNameGuid)) {
                    guidsToRemove.add(compNameGuid);
                }
            }
            for (String toRemove : guidsToRemove) {
                isChanged = true;
                assoc.removeLogicalName(toRemove);
            }
        }

        if (isChanged) {
            CompNamePM.removeUnusedCompNames(
                    GeneralStorage.getInstance().getProject().getId(), 
                    getEditorHelper().getEditSupport().getSession());
        }
 
        return isChanged;
    }

    /**
     * {@inheritDoc}
     */
    public void doSaveAs() {
        // do nothing
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSaveAsAllowed() {
        return false;
    }

    /**
     * 
     * @return the aut the editor is editing
     */
    public IAUTMainPO getAut() {
        return (IAUTMainPO)getEditorHelper().getEditSupport().getWorkVersion();
    }

    /**
     * Checks if the MasterSession is up to date.
     */
    private void checkMasterSessionUpToDate() {
        ITimestampPO objMap = getAut().getObjMap();
        final boolean isUpToDate = TimestampBP.refreshEditorNodeInMasterSession(
            objMap);
        if (!isUpToDate) {
            CompletenessBP.getInstance().completeProjectCheck();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Image getDisabledTitleImage() {
        return IconConstants.DISABLED_OM_EDITOR_IMAGE;
    }

    /**
     * {@inheritDoc}
     */
    public Composite getParentComposite() {
        return getContainer().getParent();
    }

    /**
     * {@inheritDoc}
     */
    public void reOpenEditor(IPersistentObject obj) throws PMException {
        getEditorHelper().setDirty(false);
        getEditorHelper().getEditSupport().close();
        PersistableEditorInput input = new PersistableEditorInput(obj);
        try {
            init(getEditorSite(), input);
            // MultiPageEditorPart sets the selection provider to a 
            // MultiPageSelectionProvider during init. We want to continue
            // using our own selection provider, so we re-set it here.
            m_selectionProvider.setSelectionProviderDelegate(
                    m_pageToSelectionProvider.get(getActivePage()));
            getSite().setSelectionProvider(m_selectionProvider);
            m_treeViewerUpdater =  new ComponentNameTreeViewerUpdater(
                    getActiveTreeViewer());
            final IObjectMappingPO om = getAut().getObjMap();

            m_mappingConfigComponent.setInput(om);
            Map<TreeViewer, IObjectMappingCategoryPO> viewerToInput = 
                new HashMap<TreeViewer, IObjectMappingCategoryPO>();
            viewerToInput.put(m_compNameTreeViewer, 
                    om.getUnmappedLogicalCategory());
            viewerToInput.put(m_uiElementTreeViewer, 
                    om.getUnmappedTechnicalCategory());
            viewerToInput.put(m_mappedComponentTreeViewer, 
                    om.getMappedCategory());

            for (TreeViewer splitViewer : viewerToInput.keySet()) {
                Object [] expandedSplitViewerElements = 
                    splitViewer.getExpandedElements();
                setProviders(splitViewer, getCompMapper());
                splitViewer.setInput(viewerToInput.get(splitViewer));
                splitViewer.setExpandedElements(expandedSplitViewerElements);
                // Clearing the selection seems to help prevent the behavior 
                // noted in bug http://eclip.se/334269
                splitViewer.setSelection(StructuredSelection.EMPTY);
            }
        } catch (PartInitException e) {
            getSite().getPage().closeEditor(this, false);
        }
    }

    /**
     * Assigns new (Object Mapping related) content and label providers to 
     * the given viewer.
     * 
     * @param viewer The viewer to receive new providers.
     * @param compNameMapper The mapper to use to initialize the providers.
     */
    private static void setProviders(AbstractTreeViewer viewer,
            IWritableComponentNameMapper compNameMapper) {
        viewer.setLabelProvider(
                new OMEditorTreeLabelProvider(compNameMapper));
        viewer.setContentProvider(
                new OMEditorTreeContentProvider(compNameMapper));
    }
    
    /**
     * {@inheritDoc}
     */
    public void setFocus() {
        if (getActivePage() == SPLIT_PAGE_IDX) {
            if (!m_compNameTreeViewer.getSelection().isEmpty()) {
                m_compNameTreeViewer.getControl().setFocus();
            } else if (!m_uiElementTreeViewer.getSelection().isEmpty()) {
                m_uiElementTreeViewer.getControl().setFocus();
            } else {
                m_mappedComponentTreeViewer.getControl().setFocus();
            }
        } else {
            super.setFocus();
        }
        Plugin.showStatusLine(this);
    }

    /**
     * {@inheritDoc}
     */
    public void fireDirtyProperty(boolean isDirty) {
        // fire property for change of dirty state
        firePropertyChange(IEditorPart.PROP_DIRTY);
        if (!isDirty) {
            firePropertyChange(IEditorPart.PROP_INPUT);
        }
    }

    /**
     * {@inheritDoc}
     */
    public JBEditorHelper getEditorHelper() {
        return m_editorHelper;
    }

    /**
     * {@inheritDoc}
     */
    public String getEditorPrefix() {
        return Messages.ObjectMappingEditorEditor;
    }

    /**
     * {@inheritDoc}
     */
    public void initTextAndInput(IEditorSite site, IEditorInput input) {
        setSite(site);
        setInput(input);
        setPartName(getEditorPrefix() + input.getName());
        getEditorSite().getActionBars().getMenuManager();
    }

    /**
     * {@inheritDoc}
     */
    public void update(final int event, final Object obj) {
        Plugin.getDisplay().syncExec(new Runnable() {
            @SuppressWarnings("synthetic-access")
            public void run() {
                switchEvent(event, obj);
            }

        });
    }

    /**
     * inserts a new Technical Name into GUIModel
     * 
     * @param components
     *            IComponentIdentifier
     */
    private void createNewTechnicalNames(
        final IComponentIdentifier[] components) {
        if (getEditorHelper().requestEditableState() != EditableState.OK) {
            return;
        }
        List<IObjectMappingAssoziationPO> alteredOMAs = 
                new ArrayList<IObjectMappingAssoziationPO>();
        final IObjectMappingPO objMap = getAut().getObjMap();
        for (IComponentIdentifier component : components) {
            IObjectMappingAssoziationPO techNameAssoc = objMap
                    .addTechnicalName(component, getAut());
            if (techNameAssoc != null) {
                final IObjectMappingCategoryPO categoryToCreateIn = m_omEditorBP
                        .getCategoryToCreateIn();
                if (categoryToCreateIn != null) {
                    categoryToCreateIn.addAssociation(techNameAssoc);
                } else {
                    objMap.getUnmappedTechnicalCategory().addAssociation(
                            techNameAssoc);
                }
                alteredOMAs.add(techNameAssoc);
            } else {
                // Technical Name already exists
                for (IObjectMappingAssoziationPO assoc : objMap.getMappings()) {
                    IComponentIdentifier techName = assoc.getTechnicalName();
                    if (techName != null && techName.equals(component)) {
                        techNameAssoc = assoc;
                        techNameAssoc.setCompIdentifier(component);
                        alteredOMAs.add(techNameAssoc);
                        break;
                    }
                }
            }
        }
        
        DataEventDispatcher ded = DataEventDispatcher.getInstance();
        ded.fireDataChangedListener(objMap,
                DataState.StructureModified, UpdateState.onlyInEditor);
        for (IObjectMappingAssoziationPO alteredOMA : alteredOMAs) {
            ded.fireDataChangedListener(alteredOMA,
                    DataState.StructureModified, UpdateState.onlyInEditor);
            
            IStructuredSelection techNameSelection = 
                    new StructuredSelection(alteredOMA);
            m_uiElementTreeViewer.setSelection(techNameSelection);
            m_mappedComponentTreeViewer.setSelection(techNameSelection);
        }
        getSite().getPage().activate(this);
        
        if (!alteredOMAs.isEmpty()) {
            getEditorHelper().setDirty(true);
            refreshAllViewer();
        }
    }
    
    /**
     * call refresh() for all the different viewers in this editor
     */
    private void refreshAllViewer() {
        m_uiElementTreeViewer.refresh();
        m_mappedComponentTreeViewer.refresh();
    }
    
    /**
     * executes the right update
     * @param event
     *      int
     * @param obj
     *      Object
     */
    private void switchEvent(int event, Object obj) {
        switch (event) {
            case IObjectMappingObserver.EVENT_STEP_RECORDED :
                IAUTMainPO aut = (IAUTMainPO)obj;
                if (getAut() == aut) {
                    cleanupNames();
                }
                break;
            case IObjectMappingObserver.EVENT_COMPONENT_MAPPED :
                IAUTMainPO connectedAut = 
                    TestExecution.getInstance().getConnectedAut();
                if (getAut().equals(connectedAut)) {
                    IComponentIdentifier[] comp = (IComponentIdentifier[])obj;
                    createNewTechnicalNames(comp);
                }
                break;
            default:
        }
    }

    /**
     * {@inheritDoc}
     */
    public void handleEditorDirtyStateChanged(
            IJBEditor gdEditor, boolean isDirty) {
        
        if (gdEditor == this) {
            IEvaluationService service = getSite().getService(
                    IEvaluationService.class);
            service.requestEvaluation(EditorPartPropertyTester.FQN_IS_DIRTY);
        }
    }

    /**
     * @return Returns the omEditorBP.
     */
    public OMEditorBP getOmEditorBP() {
        return m_omEditorBP;
    }
    
    /**
     * {@inheritDoc}
     */
    public TreeViewer getTreeViewer() {
        return getActiveTreeViewer();
    }

    /**
     * removed all not used logical names
     * @return int the number of added items
     */
    public int cleanupNames() {
        int addedItems = 0;
        Set<IObjectMappingAssoziationPO> addedNodes = 
            new HashSet<IObjectMappingAssoziationPO>();
        for (ITestSuitePO ts : TestSuiteBP.getListOfTestSuites()) {
            if (ts.getAut() == null) {
                continue;
            }
            if (ts.getAut().equals(getAut())) {
                CollectLogicalNamesOp op = new CollectLogicalNamesOp();
                TreeTraverser traverser = new TreeTraverser(ts, op);
                traverser.traverse(true);
                addedItems += op.getAddedNodeCount();
                addedNodes.addAll(op.getAddedNodes());
            }
        }
        if (addedItems > 0) {
            getEditorHelper().setDirty(true);
        }
        if (!isDirty()) {
            
            try {
                getEditorHelper().getEditSupport().reinitializeEditSupport();
                getEditorHelper().resetEditableState();
            } catch (PMException e) {
                PMExceptionHandler.handlePMExceptionForEditor(e, this);
            }
        }
        return addedItems;
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChanged(Object source, int propId) {
        if (propId == IWorkbenchPartConstants.PROP_DIRTY) {
            ((IEditorPart)source).isDirty();
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDirty() {
        return super.isDirty() || getEditorHelper().isDirty();
    }
    
    /**
     * {@inheritDoc}
     */
    public void dispose() {
        ObjectMappingEventDispatcher.removeObserver(this);
        OpenOMETracker.INSTANCE.removeOME(this);
        getEditorSite().getActionBars().setGlobalActionHandler(
                ActionFactory.REFRESH.getId(), null);
        IAUTMainPO connectedAut = TestExecution.getInstance().getConnectedAut();
        if (AUTModeChangedCommand.getAutMode() 
                == ChangeAUTModeMessage.OBJECT_MAPPING
                && connectedAut != null
                && connectedAut.equals(getAut())) {
            TestExecutionContributor.getInstance()
                .getClientTest().resetToTesting();
            DataEventDispatcher.getInstance()
                .fireOMStateChanged(OMState.notRunning);
        }        
        getSite().setSelectionProvider(null);
        GuiEventDispatcher.getInstance().removeEditorDirtyStateListener(this);
        
        m_treeViewerUpdater = null;
        if (m_editorHelper != null) {
            m_editorHelper.dispose();
        }
        super.dispose();
    }
    
    /**
     * {@inheritDoc}
     */
    public Object getAdapter(Class adapter) {
        Object superAdapter = super.getAdapter(adapter);
        if (superAdapter != null) {
            return superAdapter;
        }
        
        if (m_editorHelper != null) {
            return m_editorHelper.getAdapter(adapter);
        }
        
        return null;
    }

    /** {@inheritDoc} */
    public void handleDataChanged(DataChangedEvent... events) {
        for (DataChangedEvent e : events) {
            handleDataChanged(e.getPo(), e.getDataState());
        }
    }
    
    /** {@inheritDoc} */
    public void handleDataChanged(IPersistentObject po, DataState dataState) {

        getEditorHelper().handleDataChanged(po, dataState);
        if (m_treeViewerUpdater != null) {
            m_treeViewerUpdater.handleDataChanged(po, dataState);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public void init(IEditorSite site, 
            IEditorInput input) throws PartInitException {
        super.init(site, input);
        
        if (m_editorHelper == null) {
            m_editorHelper = new JBEditorHelper(this);
        }
        m_editorHelper.init(site, input);
    }

    /**
     * {@inheritDoc}
     */
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);

        m_selectionProvider.setSelectionProviderDelegate(
                m_pageToSelectionProvider.get(newPageIndex));
        
    }

    /**
     * Convenience method for accessing the editor's component name mapper.
     * 
     * @return the editor's component name mapper.
     */
    private IWritableComponentNameMapper getCompMapper() {
        return m_compMapper;
    }

    /**
     * {@inheritDoc}
     */
    public TreeViewer getActiveTreeViewer() {
        return m_activeTreeViewer;
    }

    /**
     * {@inheritDoc}
     */
    public TreeViewer[] getTreeViewers() {
        return new TreeViewer[] { 
            m_compNameTreeViewer, 
            m_uiElementTreeViewer,
            m_mappedComponentTreeViewer };
    }
    
    /**
     * 
     * @return the component name TreeViewer
     */
    public TreeViewer getCompNameTreeViewer() {
        return m_compNameTreeViewer;
    }
    
    /**
     * 
     * @return the UI-Element TreeViewer
     */
    public TreeViewer getUIElementTreeViewer() {
        return m_uiElementTreeViewer;
    }
    
    /**
     * 
     * @return the mapped components TreeViewer
     */
    public TreeViewer getMappedTreeViewer() {
        return m_mappedComponentTreeViewer;
    }

    /**
     * 
     * {@inheritDoc}
     */
    public EntityManager getEntityManager() {
        return getEditorHelper().getEditSupport().getSession();
    }
}
