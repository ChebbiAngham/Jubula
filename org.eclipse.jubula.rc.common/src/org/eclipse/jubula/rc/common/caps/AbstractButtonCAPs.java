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
package org.eclipse.jubula.rc.common.caps;

import org.eclipse.jubula.rc.common.exception.StepExecutionException;
import org.eclipse.jubula.rc.common.implclasses.MatchUtil;
import org.eclipse.jubula.rc.common.implclasses.Verifier;
import org.eclipse.jubula.rc.common.uiadapter.interfaces.IButtonAdapter;


/**
 * Implementation for all Button like classes, it holds the
 * general methods for the testing of buttons.
 * 
 * @author BREDEX GmbH
 *
 */
public class AbstractButtonCAPs extends AbstractTextVerifiable {

    /**
     * 
     * @return the IButtonAdapter for the component. 
     */
    private IButtonAdapter getButtonAdapter() {
        return (IButtonAdapter)getComponent();
    }

    /**
     * Verifies the selected property.
     * 
     * @param selected The selected property value to verify.
     */
    public void gdVerifySelected(boolean selected) {

        Verifier.equals(selected, getButtonAdapter().isSelected());
    }
   
    /**
     * Verifies the passed text.
     * 
     * @param text The text to verify
     */
    public void gdVerifyText(String text) {
        gdVerifyText(text, MatchUtil.DEFAULT_OPERATOR);
    }

    /** {@inheritDoc} */
    public String[] getTextArrayFromComponent() {
        String[] textArray = null;
        try {
            textArray = new String[] { getButtonAdapter().getText() };
        } catch (StepExecutionException e) {
            // ok here - getText() might be an unsupported action
        }
        return textArray;
    }
}
