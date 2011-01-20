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
package org.eclipse.jubula.client.ui.provider.labelprovider.decorators;

import java.util.Locale;

import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jubula.client.core.model.IAUTMainPO;
import org.eclipse.jubula.client.core.model.ICapPO;
import org.eclipse.jubula.client.core.model.ICompNamesPairPO;
import org.eclipse.jubula.client.core.model.IExecTestCasePO;
import org.eclipse.jubula.client.core.model.INodePO;
import org.eclipse.jubula.client.core.model.ITestSuitePO;
import org.eclipse.jubula.client.ui.businessprocess.WorkingLanguageBP;
import org.eclipse.jubula.client.ui.constants.IconConstants;
import org.eclipse.jubula.client.ui.constants.Layout;
import org.eclipse.jubula.client.ui.editors.AbstractJBEditor.JBEditorDecorationContext;
import org.eclipse.jubula.client.ui.model.GuiNode;
import org.eclipse.jubula.client.ui.model.RefTestSuiteGUI;
import org.eclipse.jubula.client.ui.model.TestJobGUI;
import org.eclipse.jubula.client.ui.model.TestSuiteGUI;
import org.eclipse.jubula.client.ui.provider.labelprovider.TestSuiteBrowserLabelProvider;


/**
 * @author BREDEX GmbH
 * @created 10.05.2005
 */
public class TestDataDecorator extends TestSuiteBrowserLabelProvider implements
    ILightweightLabelDecorator {

    /**
     * {@inheritDoc}
     */
    public void decorate(Object element, IDecoration decoration) {
        decoration.setForegroundColor(Layout.DEFAULT_OS_COLOR);
        GuiNode gnode = (GuiNode)element;
        INodePO node = gnode.getContent();
        if (node == null || gnode.getParentNode() == null
                || TestSuiteGUI.getTestSuiteForNode(gnode) == null
                || decoration.getDecorationContext() 
                    instanceof JBEditorDecorationContext) {
            return;
        }
        boolean flag = false;
        if (isNodeActive(gnode)) {
            ITestSuitePO testSuite = (ITestSuitePO)(TestSuiteGUI
                    .getTestSuiteForNode((GuiNode)element)).getContent();
            if (testSuite != null) {
                final WorkingLanguageBP workLangBP = WorkingLanguageBP
                    .getInstance();
                Locale locale = workLangBP.getWorkingLanguage();
                IAUTMainPO aut = testSuite.getAut();
                if (element instanceof TestSuiteGUI) {
                    TestSuiteGUI execTs = (TestSuiteGUI)element;
                    if (testSuite.getAut() != null
                            && !workLangBP
                                .isTestSuiteLanguage(locale, testSuite)) {
                        decoration.setForegroundColor(Layout.GRAY_COLOR);
                    } else {
                        decoration.setForegroundColor(Layout.DEFAULT_OS_COLOR);
                        flag = node.getSumTdFlag(locale) 
                            && node.getSumOMFlag(aut) 
                            && node.getSumSpecTcFlag();
                    }
                    if (execTs.getChildren().size() == 0) {
                        flag = true;
                    }
                    if (testSuite.getAut() == null) {
                        flag = false;
                    }
                } else if (node instanceof IExecTestCasePO) {
                    flag = node.getSumTdFlag(locale) && node.getSumOMFlag(aut)
                        && node.getSumSpecTcFlag();
                } else if (node instanceof ICapPO) {
                    ICapPO cap = (ICapPO)node;
                    IExecTestCasePO execTC = (IExecTestCasePO)((GuiNode)element)
                        .getParentNode().getParentNode().getContent();
                    boolean overWrittenName = false;
                    for (ICompNamesPairPO pair : execTC.getCompNamesPairs()) {
                        if (pair.getFirstName().equals(cap.getComponentName())
                                && pair.getSecondName() != null
                                    && !pair.getSecondName().equals(
                                            cap.getComponentName())) {
                            
                            overWrittenName = true;
                            break;
                        }
                    }
                    flag = cap.getCompleteTdFlag(locale);
                    if (!overWrittenName) {
                        flag = flag && cap.getCompleteOMFlag(aut);
                    }
                }
            } else {
                if (element instanceof TestJobGUI) {
                    flag = isTestJobGuiValid((TestJobGUI)element);
                } else if (element instanceof RefTestSuiteGUI) {
                    flag = isRefTestSuiteGuiValid((RefTestSuiteGUI)element);
                } else {
                    flag = true;
                }
            }
        } else {
            flag = true;
        }
        setIcon(decoration, flag);
    }

    /**
     * @param decoration decoration
     * @param flag flag for choice of wanted icon
     */
    private void setIcon(IDecoration decoration, boolean flag) {
        if (!flag) {
            decoration.addOverlay(
                    IconConstants.INCOMPLETE_DATA_IMAGE_DESCRIPTOR);
        }
    }
}