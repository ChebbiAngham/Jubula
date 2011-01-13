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
package org.eclipse.jubula.client.ui.dialogs;

import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jubula.client.core.model.ISpecTestCasePO;
import org.eclipse.jubula.client.ui.provider.GDControlDecorator;
import org.eclipse.jubula.tools.i18n.I18n;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;


/**
 * @author BREDEX GmbH
 * @created Oct 25, 2007
 */
@SuppressWarnings("synthetic-access")
public final class EditParametersDialog extends AbstractEditParametersDialog {
    /** The ITestCasePO */
    private ISpecTestCasePO m_specTc;
    
    /** true if imterface is locked, false otherwise */
    private boolean m_isInterfaceLocked;

    /** The Lock Interface CheckBox */
    private Button m_lockInterfaceCheckBox;

    /**
     * @param parentShell the parent.
     * @param testCase the ITestCasePO.
     */
    public EditParametersDialog(Shell parentShell, 
        ISpecTestCasePO testCase) {
        
        super(parentShell, testCase);
        m_specTc = testCase;
        m_isInterfaceLocked = m_specTc.isInterfaceLocked();
    }
    
    /**
     * {@inheritDoc}
     */
    protected Control createDialogArea(Composite parent) {
        final String dialogTitle = I18n
                .getString("EditParametersDialog.EditParameters"); //$NON-NLS-1$
        setTitle(dialogTitle);
        setMessage(I18n.getString("EditParametersDialog.EditParamsOfTestCase")); //$NON-NLS-1$

        getShell().setText(dialogTitle);
        return super.createDialogArea(parent);
    }
    
    /**
     * This Listener enables/disables the given Component depending on
     * the status of the observed button to which this listener is added.<br>
     * <b>Note: Only add this Listener to Buttons!</b>
     * @author BREDEX GmbH
     * @created Mar 10, 2008
     */
    private static final class ButtonSelectedListener 
        implements SelectionListener {
        
        /**
         * A Control
         */
        private Control m_component;

        /**
         * Constructor
         * @param control a Control
         */
        public ButtonSelectedListener(Control control) {
            m_component = control;
        }
        
        /**
         * {@inheritDoc}
         */
        public void widgetDefaultSelected(SelectionEvent e) {
            handleSelection(e);
        }

        /**
         * {@inheritDoc}
         */
        public void widgetSelected(SelectionEvent e) {
            handleSelection(e);
        }
        
        /**
         * Handles the SelectionEvent
         * @param e SelectionEvent.
         */
        private void handleSelection(SelectionEvent e) {
            final Button button = (Button)e.widget;
            m_component.setEnabled(!button.getSelection());
            
        }
    }
    
    /**
     * {@inheritDoc}
     */
    protected boolean generalButtonEnablement() {
        return !isInterfaceLocked();
    }

    /**
     * {@inheritDoc}
     */
    protected ICellModifier getParamTableCellModifier() {
        return new ParamTableCellModifier();
    }
    
    /**
     * @author BREDEX GmbH
     * @created Oct 25, 2007
     */
    private final class ParamTableCellModifier extends
            AbstractTableCellModifier {
        /**
         * {@inheritDoc}
         */
        public boolean canModify(Object element, String property) {
            return !m_isInterfaceLocked;
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void createAdditionalWidgetsAtTop(Composite parent) {
        createLockInterfaceCheckBox(parent);
    }
    
    /**
     * Creates the Lock Interface CheckBox
     * @param parent the parent
     */
    private void createLockInterfaceCheckBox(Composite parent) {
        m_lockInterfaceCheckBox = new Button(parent, SWT.CHECK);
        m_lockInterfaceCheckBox.setText(I18n.getString("EditParametersDialog.LockInterface")); //$NON-NLS-1$
        m_lockInterfaceCheckBox.setSelection(m_isInterfaceLocked);
        m_lockInterfaceCheckBox.addSelectionListener(new SelectionListener() {

            public void widgetDefaultSelected(SelectionEvent e) {
                handleSelection(e);
            }

            public void widgetSelected(SelectionEvent e) {
                handleSelection(e);
            }
            
            private void handleSelection(SelectionEvent e) {
                final Button checkBox = (Button)e.widget;
                m_isInterfaceLocked = checkBox.getSelection();
            }
            
        });
        GDControlDecorator.decorateInfo(m_lockInterfaceCheckBox, 
                "GDControlDecorator.EditParameterLock", false);
    }

    /**
     * {@inheritDoc}
     */
    protected void createParameterTable(Composite parent) {
        super.createParameterTable(parent);
        Table table = getParamTableViewer().getTable();
        
        m_lockInterfaceCheckBox.addSelectionListener(
                new ButtonSelectedListener(table));
        table.setEnabled(!m_isInterfaceLocked);
    }

    /**
     * {@inheritDoc}
     */
    protected void afterDeleteButtonCreation(Button deleteButton,
            SelectionBasedButtonEnabler buttonEnabler) {
        m_lockInterfaceCheckBox.addSelectionListener(buttonEnabler);
        deleteButton.setEnabled(!m_isInterfaceLocked);
    }

    /**
     * {@inheritDoc}
     */
    protected void afterUpButtonCreation(Button upButton,
            SelectionBasedButtonEnabler buttonEnabler) {
        m_lockInterfaceCheckBox.addSelectionListener(buttonEnabler);
        upButton.setEnabled(!m_isInterfaceLocked);
    }
    
    /**
     * {@inheritDoc}
     */
    protected void afterDownButtonCreation(
            Button downButton,
            SelectionBasedButtonEnabler buttonEnabler) {
        m_lockInterfaceCheckBox.addSelectionListener(buttonEnabler);
        downButton.setEnabled(!m_isInterfaceLocked);
    }

    /**
     * {@inheritDoc}
     */
    protected void afterAddButtonCreation(Button addButton) {
        m_lockInterfaceCheckBox
                .addSelectionListener(new ButtonSelectedListener(addButton));
        addButton.setEnabled(!m_isInterfaceLocked);
    }
    
    /**
     * @return the isInterfaceLocked
     */
    public boolean isInterfaceLocked() {
        return m_isInterfaceLocked;
    }

    /**
     * {@inheritDoc}
     */
    protected String getEditedObjectNameString() {
        return I18n.getString("EditParametersDialog.TestCaseName"); //$NON-NLS-1$
    }
}
