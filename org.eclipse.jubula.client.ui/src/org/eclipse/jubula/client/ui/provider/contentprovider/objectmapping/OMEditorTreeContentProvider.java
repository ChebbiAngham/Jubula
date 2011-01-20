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
package org.eclipse.jubula.client.ui.provider.contentprovider.objectmapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.Validate;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jubula.client.core.businessprocess.IComponentNameMapper;
import org.eclipse.jubula.client.core.events.DataEventDispatcher;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.DataState;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.IDataChangedListener;
import org.eclipse.jubula.client.core.events.DataEventDispatcher.UpdateState;
import org.eclipse.jubula.client.core.model.IComponentNamePO;
import org.eclipse.jubula.client.core.model.IObjectMappingAssoziationPO;
import org.eclipse.jubula.client.core.model.IObjectMappingCategoryPO;
import org.eclipse.jubula.client.core.model.IObjectMappingPO;
import org.eclipse.jubula.client.core.model.IPersistentObject;
import org.eclipse.jubula.client.ui.provider.contentprovider.AbstractTreeViewContentProvider;
import org.eclipse.jubula.tools.exception.Assert;


/**
 * @author BREDEX GmbH
 * @created 19.04.2005
 */
public class OMEditorTreeContentProvider extends
    AbstractTreeViewContentProvider {

    /** mapping from each child object to its parent */
    private Map<Object, Object> m_childToParentMap = 
        new HashMap<Object, Object>();
    
    /** used for finding Component Names */
    private IComponentNameMapper m_compNameMapper;
    
    /** listener for updates to the model */
    private IDataChangedListener m_modelListener;
    
    /**
     * Constructor.
     * 
     * @param compNameMapper The mapper to use for finding Component Names.
     */
    public OMEditorTreeContentProvider(IComponentNameMapper compNameMapper) {
        m_compNameMapper = compNameMapper;
    }

    /**
     * 
     * {@inheritDoc}
     */
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof IObjectMappingPO) {
            IObjectMappingPO mapping = (IObjectMappingPO)parentElement;
            List<IObjectMappingCategoryPO> categoryList = 
                new ArrayList<IObjectMappingCategoryPO>();

            categoryList.add(mapping.getMappedCategory());
            categoryList.add(mapping.getUnmappedLogicalCategory());
            categoryList.add(mapping.getUnmappedTechnicalCategory());

            Validate.noNullElements(categoryList);
            return categoryList.toArray();
        }
        
        if (parentElement instanceof IObjectMappingAssoziationPO) {
            IObjectMappingAssoziationPO assoc = 
                (IObjectMappingAssoziationPO)parentElement;
            List<String> componentNameGuidList = assoc.getLogicalNames();
            List<Object> componentNamePoList = new ArrayList<Object>();
            for (String compNameGuid : componentNameGuidList) {
                IComponentNamePO compNamePo = 
                    m_compNameMapper.getCompNameCache().getCompNamePo(
                            compNameGuid);
                if (compNamePo != null) {
                    componentNamePoList.add(compNamePo);
                    m_childToParentMap.put(compNamePo, parentElement);
                } else {
                    componentNamePoList.add(compNameGuid);
                    m_childToParentMap.put(compNamePo, parentElement);
                }
            }
            Validate.noNullElements(componentNamePoList);
            return componentNamePoList.toArray();
        }

        if (parentElement instanceof IComponentNamePO) {
            return new Object[0];
        }
        
        if (parentElement instanceof IObjectMappingCategoryPO) {
            List<Object> childList = new ArrayList<Object>();
            IObjectMappingCategoryPO category = 
                (IObjectMappingCategoryPO)parentElement;
            childList.addAll(category.getUnmodifiableCategoryList());
            for (IObjectMappingAssoziationPO assoc 
                    : category.getUnmodifiableAssociationList()) {
                if (assoc.getTechnicalName() != null) {
                    childList.add(assoc);
                } else {
                    for (String compNameGuid : assoc.getLogicalNames()) {
                        IComponentNamePO compName = 
                            m_compNameMapper.getCompNameCache()
                                .getCompNamePo(compNameGuid);
                        if (compName != null) {
                            childList.add(compName);
                        } else {
                            // Missing Component Name
                            childList.add(compNameGuid);
                        }
                    }
                }
            }

            for (Object child : childList) {
                m_childToParentMap.put(child, parentElement);
            }
            
            Validate.noNullElements(childList);
            return childList.toArray();
        } else if (parentElement instanceof String) {
            return new Object[0];
        }
        Assert.notReached("Wrong type of element!"); //$NON-NLS-1$
        return new Object[0];
    }

    /**
     * 
     * {@inheritDoc}
     */
    public Object getParent(Object element) {
        if (element instanceof IObjectMappingAssoziationPO) {
            return ((IObjectMappingAssoziationPO)element).getCategory();
        } else if (element instanceof IObjectMappingCategoryPO) {
            IObjectMappingCategoryPO parent = 
                ((IObjectMappingCategoryPO)element).getParent();
            if (parent != null) {
                return parent;
            }
        }
        return m_childToParentMap.get(element);
    }

    /**
     * @param element     Object
     * @return boolean    returnVal
     */
    public boolean hasChildren(Object element) {
        return getChildren(element).length > 0;
    }

    /**
     * @param inputElement     Object
     * @return Object[]         returnVal
     */
    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }
    
    /**
     * {@inheritDoc}
     */
    public void dispose() {
        m_childToParentMap.clear();
        super.dispose();
    }

    /**
     * {@inheritDoc}
     */
    public void inputChanged(final Viewer viewer, Object oldInput, 
            final Object newInput) {
        Validate.isTrue(viewer instanceof TreeViewer);
        if (m_modelListener != null) {
            DataEventDispatcher.getInstance()
                .removeDataChangedListener(m_modelListener);
            m_modelListener = null;
        }

        if (newInput != null) {
            m_modelListener = new IDataChangedListener() {
                
                public void handleDataChanged(IPersistentObject po, 
                        DataState dataState, UpdateState updateState) {
                    
                    if (updateState != UpdateState.notInEditor) {
                        StructuredViewer structuredViewer = 
                            (StructuredViewer)viewer;
                        if (dataState == DataState.StructureModified) {
                            if (newInput.equals(po)) {
                                structuredViewer.refresh();
                            } else {
                                structuredViewer.refresh(po);
                            }
                        } else if (dataState == DataState.Renamed) {
                            structuredViewer.update(po, null);
                        }
                    }
                }
            };
            DataEventDispatcher.getInstance().addDataChangedListener(
                    m_modelListener, true);
        }
    }
}