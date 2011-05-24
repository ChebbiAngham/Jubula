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
package org.eclipse.jubula.client.ui.provider.contentprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.jubula.client.core.model.IExecTestCasePO;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.IProjectPO;
import org.eclipse.jubula.client.core.model.IReusedProjectPO;
import org.eclipse.jubula.client.core.model.ISpecTestCasePO;
import org.eclipse.jubula.client.core.persistence.ProjectPM;
import org.eclipse.jubula.client.ui.utils.Utils;
import org.eclipse.jubula.tools.exception.JBException;

/**
 * @author BREDEX GmbH
 * @created 08.10.2004
 */
public class TestCaseBrowserContentProvider 
    extends AbstractTreeViewContentProvider {

    /**
     * {@inheritDoc}
     * @param parentElement Object
     * @return object array
     */
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof IProjectPO[]) {
            return new Object[] { ((IProjectPO[])parentElement)[0] };
        }

        if (parentElement instanceof IProjectPO) {
            IProjectPO project = (IProjectPO)parentElement;
            List<Object> elements = new ArrayList<Object>();
            elements.addAll(project.getSpecObjCont().getSpecObjList());
            elements.addAll(project.getUsedProjects());
            return elements.toArray();
        }
        
        if (parentElement instanceof IExecTestCasePO) {
            ISpecTestCasePO referencedTestCase = 
                ((IExecTestCasePO)parentElement).getSpecTestCase();
            if (referencedTestCase != null) {
                return ArrayUtils.addAll(
                        Collections.unmodifiableCollection(
                                referencedTestCase.getAllEventEventExecTC())
                                .toArray(), referencedTestCase
                                .getUnmodifiableNodeList().toArray());
            }
            
            return ArrayUtils.EMPTY_OBJECT_ARRAY;
        }

        if (parentElement instanceof INodePO) {
            INodePO parentNode = ((INodePO) parentElement);
            Object[] children = parentNode.getUnmodifiableNodeList().toArray();
            if (parentElement instanceof ISpecTestCasePO) {
                children = ArrayUtils.addAll(
                        Collections.unmodifiableCollection(
                                ((ISpecTestCasePO) parentElement)
                                        .getAllEventEventExecTC()).toArray(),
                        children);
            }
            return children;
        }
        
        if (parentElement instanceof IReusedProjectPO) {
            try {
                IProjectPO reusedProject = 
                    ProjectPM.loadReusedProjectInMasterSession(
                            (IReusedProjectPO)parentElement);

                if (reusedProject != null) {
                    return reusedProject.getSpecObjCont()
                        .getSpecObjList().toArray();
                }

                return ArrayUtils.EMPTY_OBJECT_ARRAY;
            } catch (JBException e) {
                Utils.createMessageDialog(e, null, null);
                return ArrayUtils.EMPTY_OBJECT_ARRAY;
            }
        }
        
        return ArrayUtils.EMPTY_OBJECT_ARRAY;
    }  
}