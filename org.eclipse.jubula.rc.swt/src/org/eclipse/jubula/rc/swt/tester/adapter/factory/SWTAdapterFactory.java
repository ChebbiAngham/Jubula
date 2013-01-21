/*******************************************************************************
 * Copyright (c) 2012 BREDEX GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BREDEX GmbH - initial API and implementation 
 *******************************************************************************/
package org.eclipse.jubula.rc.swt.tester.adapter.factory;

import org.eclipse.jubula.rc.common.tester.adapter.factory.IUIAdapterFactory;
import org.eclipse.jubula.rc.common.tester.adapter.interfaces.IComponentAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.ButtonAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.CComboAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.CLabelAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.CTabFolderAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.ComboAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.LabelAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.ListAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.MenuAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.MenuItemAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.StyledTextAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.TabFolderAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.TableAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.TextComponentAdapter;
import org.eclipse.jubula.rc.swt.tester.adapter.TreeAdapter;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;

/**
 * This factory constructs the specific adapter out of the incoming
 * graphics component from the AUT.
 * 
 * @author BREDEX GmbH
 */
public class SWTAdapterFactory implements IUIAdapterFactory {
    /** */
    private static final Class[] SUPPORTEDCLASSES = 
            new Class[]{Button.class, Menu.class, MenuItem.class, Tree.class,
                Table.class, List.class, Text.class, StyledText.class,
                Combo.class, CCombo.class, Label.class, CLabel.class, 
                TabFolder.class, CTabFolder.class};
    
    
    /**
     * {@inheritDoc}
     */
    public Class[] getSupportedClasses() {
        return SUPPORTEDCLASSES;
    }

    /**
     * {@inheritDoc}
     */
    public IComponentAdapter getAdapter(Object objectToAdapt) {
        IComponentAdapter returnvalue = null;
        if (objectToAdapt instanceof Button) {
            returnvalue = new ButtonAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof Menu) {
            returnvalue = new MenuAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof MenuItem) {
            returnvalue = new MenuItemAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof Tree) {
            returnvalue = new TreeAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof Table) {
            returnvalue = new TableAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof List) {
            returnvalue = new ListAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof Text) {
            returnvalue = new TextComponentAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof StyledText) {
            returnvalue = new StyledTextAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof Combo) {
            returnvalue = new ComboAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof CCombo) {
            returnvalue = new CComboAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof Label) {
            returnvalue = new LabelAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof CLabel) {
            returnvalue = new CLabelAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof TabFolder) {
            returnvalue = new TabFolderAdapter(objectToAdapt);
        } else if (objectToAdapt instanceof CTabFolder) {
            returnvalue = new CTabFolderAdapter(objectToAdapt);
        }
        
        return returnvalue;
    }

}
