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
package org.eclipse.jubula.rc.common.commands;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang.Validate;
import org.eclipse.jubula.communication.internal.ICommand;
import org.eclipse.jubula.communication.internal.message.CAPTestMessage;
import org.eclipse.jubula.communication.internal.message.CAPTestResponseMessage;
import org.eclipse.jubula.communication.internal.message.ChangeAUTModeMessage;
import org.eclipse.jubula.communication.internal.message.Message;
import org.eclipse.jubula.communication.internal.message.MessageCap;
import org.eclipse.jubula.communication.internal.message.MessageParam;
import org.eclipse.jubula.rc.common.AUTServer;
import org.eclipse.jubula.rc.common.AUTServerConfiguration;
import org.eclipse.jubula.rc.common.exception.ComponentNotFoundException;
import org.eclipse.jubula.rc.common.exception.EventSupportException;
import org.eclipse.jubula.rc.common.exception.ExecutionEvent;
import org.eclipse.jubula.rc.common.exception.MethodParamException;
import org.eclipse.jubula.rc.common.exception.StepExecutionException;
import org.eclipse.jubula.rc.common.exception.StepVerifyFailedException;
import org.eclipse.jubula.rc.common.exception.UnsupportedComponentException;
import org.eclipse.jubula.rc.common.tester.WidgetTester;
import org.eclipse.jubula.rc.common.util.Verifier;
import org.eclipse.jubula.tools.internal.constants.TimingConstantsServer;
import org.eclipse.jubula.tools.internal.i18n.CompSystemI18n;
import org.eclipse.jubula.tools.internal.objects.IComponentIdentifier;
import org.eclipse.jubula.tools.internal.objects.event.EventFactory;
import org.eclipse.jubula.tools.internal.objects.event.TestErrorEvent;
import org.eclipse.jubula.tools.internal.utils.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class gets an message with ICommand action parameter triples. It invokes
 * the implementation class and executes the method. Then it creates a
 * <code>CAPTestResponseMessage</code> and sends it back to the client. The
 * <code>CAPTestResponseMessage</code> contains an error event only if the
 * test step fails, due to a problem prior to or during the execution of the
 * implementation class action method.
 * @author BREDEX GmbH
 * @created 02.01.2007
 */
public class CapTestCommand implements ICommand {
    /** The logger */
    private static final Logger LOG = LoggerFactory.getLogger(
        CapTestCommand.class);
    
    /** The message. */
    private CAPTestMessage m_capTestMessage;

    /**
     * {@inheritDoc}
     */
    public Message getMessage() {
        return m_capTestMessage;
    }
    /**
     * {@inheritDoc}
     */
    public void setMessage(Message message) {
        m_capTestMessage = (CAPTestMessage)message;
    }
    
    /**
     * Is called if the graphics component cannot be found. Logs the error and
     * sets the action error event into the message.
     * @param response The response message
     * @param e The exception.
     */
    private void handleComponentNotFound(CAPTestResponseMessage response,
        Throwable e) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(e.getLocalizedMessage(), e);
        }
        response.setTestErrorEvent(EventFactory
                .createComponentNotFoundErrorEvent());
    }

    /**
     * Is called if one or more CAP parameters are invalid. Logs the error and
     * sets the action error event into the message.
     * @param e The error message.
     */
    private void handleInvalidInput(String e) {
        throw new StepExecutionException(e, EventFactory
                .createImplClassErrorEvent());
    }

    /**
     * Gets the implementation class. 
     * @param response The response message.
     * @return the implementation class or null if an error occurs.
     */
    protected Object getImplClass(CAPTestResponseMessage response) {
        Object implClass = null;
        final MessageCap messageCap = m_capTestMessage.getMessageCap();
        IComponentIdentifier ci = messageCap.getCi();
        if (LOG.isInfoEnabled()) {
            LOG.info("component class name: " //$NON-NLS-1$
                + (ci == null ? "(none)" : ci.getComponentClassName())); //$NON-NLS-1$
        }
        try {
            if (!messageCap.hasDefaultMapping()) {
                Validate.notNull(ci);
            }
            // FIXME : Extra handling for waitForComponent and verifyExists
            int timeout = TimingConstantsServer.DEFAULT_FIND_COMPONENT_TIMEOUT;
            boolean isWaitForComponent = 
                    WidgetTester.RC_METHOD_NAME_WAIT_FOR_COMPONENT
                        .equals(messageCap.getMethod());
            if (isWaitForComponent) { 
                MessageParam timeoutParam = (MessageParam)messageCap.
                        getMessageParams().get(0);
                try {
                    timeout = Integer.parseInt(timeoutParam.getValue());
                } catch (NumberFormatException e) {
                    LOG.warn("Error while parsing timeout parameter. " //$NON-NLS-1$
                        + "Using default value.", e); //$NON-NLS-1$
                }
            }
            final AUTServerConfiguration rcConfig = AUTServerConfiguration
                .getInstance();
            if (!messageCap.hasDefaultMapping()) {
                Object component = AUTServer.getInstance().findComponent(ci,
                        timeout);
                implClass = rcConfig.prepareImplementationClass(component,
                    component.getClass());
            } else {
                implClass = rcConfig.getImplementationClass(ci
                    .getComponentClassName());
            }
            if (isWaitForComponent) {
                MessageParam delayParam = (MessageParam)messageCap.
                        getMessageParams().get(1);
                try {
                    int delay = Integer.parseInt(delayParam.getValue());
                    TimeUtil.delay(delay);
                } catch (IllegalArgumentException iae) {
                    handleInvalidInput("Invalid input: " //$NON-NLS-1$
                        + CompSystemI18n.getString("CompSystem.DelayAfterVisibility") //$NON-NLS-1$
                        + " must be a non-negative integer."); //$NON-NLS-1$
                }
            }
        } catch (IllegalArgumentException e) {
            handleComponentNotFound(response, e);
        } catch (ComponentNotFoundException e) {
            if (WidgetTester.RC_METHOD_NAME_CHECK_EXISTENCE
                    .equals(messageCap.getMethod())) {
                MessageParam isVisibleParam = 
                    (MessageParam)messageCap.getMessageParams().get(0);
                handleComponentDoesNotExist(response, 
                    Boolean.valueOf(isVisibleParam.getValue())
                        .booleanValue());
            } else {
                handleComponentNotFound(response, e);
            }
        } catch (UnsupportedComponentException buce) {
            LOG.error(buce.getLocalizedMessage(), buce);
            response.setTestErrorEvent(EventFactory.createConfigErrorEvent());
        } catch (Throwable e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getLocalizedMessage(), e);
            }
            response.setTestErrorEvent(
                    EventFactory.createImplClassErrorEvent());
        } 
        return implClass;
    }
    
    /**
     * Handles the scenario where a component does not exist, but may also
     * not be expected to exist.
     * Is called if the graphics component cannot be found and the current 
     * request is attempting to verify the existence/non-existence of that 
     * component.
     * Sets the status of the response to Verification Error if the component is
     * expected to exist. Otherwise continues normal operation.

     * @param response The response message
     * @param shouldExist <code>True</code> if the component is expected to
     *                    exist. Otherwise, <code>false</code>.
     */
    private void handleComponentDoesNotExist(CAPTestResponseMessage response, 
        boolean shouldExist) {
        try {
            Verifier.equals(shouldExist, false);
        } catch (StepVerifyFailedException svfe) {
            response.setTestErrorEvent(EventFactory.createVerifyFailed(
                    String.valueOf(shouldExist), String.valueOf(false)));
            
        }
    }

    /**
     * calls the method of the implementation class per reflection
     * {@inheritDoc}
     */
    public Message execute() {
        AUTServer autServer = AUTServer.getInstance();
        final int oldMode = autServer.getMode();
        TestErrorEvent event = null;
        CAPTestResponseMessage response = new CAPTestResponseMessage();
        if (oldMode != ChangeAUTModeMessage.TESTING) {
            autServer.setMode(ChangeAUTModeMessage.TESTING);
        } 
        try {
            MessageCap messageCap = m_capTestMessage.getMessageCap();
            response.setMessageCap(messageCap);
        
            // get the implementation class
            Object implClass = getImplClass(response);
            if (implClass == null) {
                return response;
            }
            MethodInvoker invoker = new MethodInvoker(messageCap);
            Object returnValue = invoker.invoke(implClass);
            response.setReturnValue((String)returnValue);
        } catch (NoSuchMethodException nsme) {
            LOG.error("implementation class method not found", nsme); //$NON-NLS-1$
            event = EventFactory.createUnsupportedActionError();
        } catch (IllegalAccessException iae) {
            LOG.error("Failed accessing implementation class method", iae); //$NON-NLS-1$
            event = EventFactory.createConfigErrorEvent();
        } catch (InvocationTargetException ite) {
            if (ite.getTargetException() instanceof EventSupportException) {
                EventSupportException e = (EventSupportException)
                    ite.getTargetException();
                event = e.getEvent();
                if (LOG.isDebugEnabled()) {
                    LOG.debug(e.getLocalizedMessage(), e);
                }
            } else if (ite.getTargetException() instanceof ExecutionEvent) {
                ExecutionEvent e = (ExecutionEvent)ite.getTargetException();
                response.setState(e.getEvent());
                if (LOG.isDebugEnabled()) {
                    LOG.debug(e.getLocalizedMessage(), e);
                }
            } else {
                event = EventFactory.createConfigErrorEvent();
                if (LOG.isErrorEnabled()) {
                    LOG.error("InvocationTargetException: ", ite); //$NON-NLS-1$
                    LOG.error("TargetException: ", ite.getTargetException()); //$NON-NLS-1$
                }
            }
        } catch (IllegalArgumentException e) {
            LOG.error(e.getLocalizedMessage(), e);
        } catch (MethodParamException e) {
            LOG.error(e.getLocalizedMessage(), e);
        } finally {
            if (autServer.getMode() != oldMode) {
                autServer.setMode(oldMode);
            }
        }
        if (event != null) {
            response.setTestErrorEvent(event);
        }
        if (m_capTestMessage.isRequestAnswer()) {
            return response;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void timeout() {
        LOG.error(this.getClass().getName() + "timeout() called"); //$NON-NLS-1$
    }
}