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
package org.eclipse.jubula.client.ui.rcp.provider.contentprovider;

import org.eclipse.jubula.client.core.model.IExecObjContPO;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IProjectPO;
import org.eclipse.jubula.client.core.model.IReusedProjectPO;
import org.eclipse.jubula.client.core.model.ISpecObjContPO;
import org.eclipse.jubula.client.core.model.ITestDataCategoryPO;
import org.eclipse.jubula.client.core.model.ITestDataCubePO;
import org.eclipse.jubula.client.core.persistence.GeneralStorage;
import org.eclipse.jubula.client.ui.rcp.i18n.Messages;
import org.eclipse.jubula.client.ui.rcp.search.result.BasicSearchResult.SearchResultElement;
import org.eclipse.jubula.tools.constants.StringConstants;
import org.eclipse.jubula.tools.exception.Assert;


/**
 * @author BREDEX GmbH
 * @created 13.09.2005
 */
public abstract class AbstractTreeViewContentProvider extends
    AbstractNodeTreeContentProvider {
    
    /** {@inheritDoc} */
    public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
    }
    
    /** {@inheritDoc} */
    public boolean hasChildren(Object element) {
        return getChildren(element).length > 0;
    }
    
    /** {@inheritDoc} */
    public Object getParent(Object element) {
        if (element instanceof INodePO) {
            INodePO parent = ((INodePO)element).getParentNode();
            IProjectPO activeProject = 
                GeneralStorage.getInstance().getProject();
            if (parent instanceof IProjectPO 
                    && !parent.equals(activeProject)) {
                // Parent is a project, but not the active project.
                // So it must be a reused project.
                String parentGuid = parent.getGuid();
                if (activeProject != null && parentGuid != null) {
                    for (IReusedProjectPO reusedProject 
                            : activeProject.getUsedProjects()) {
                        if (parentGuid.equals(reusedProject.getProjectGuid())) {
                            return reusedProject;
                        }
                    }
                }
            }
            return parent;
        }
        if (element instanceof IReusedProjectPO
                || element instanceof ISpecObjContPO
                || element instanceof IExecObjContPO) {
            return GeneralStorage.getInstance().getProject();
        }
        if (element instanceof ITestDataCategoryPO) {
            return ((ITestDataCategoryPO)element).getParent();
        }
        if (element instanceof ITestDataCubePO) {
            return ((ITestDataCubePO)element).getParent();
        }
        if (element instanceof SearchResultElement) {
            return null;
        }
        Assert.notReached(Messages.WrongTypeOfElement 
                + StringConstants.EXCLAMATION_MARK);
        return null;
    }

}