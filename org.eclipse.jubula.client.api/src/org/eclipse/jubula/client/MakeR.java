/*******************************************************************************
 * Copyright (c) 2014 BREDEX GmbH.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BREDEX GmbH - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.jubula.client;

import java.io.InputStream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jubula.client.exceptions.LoadResourceException;
import org.eclipse.jubula.client.internal.impl.AUTAgentImpl;
import org.eclipse.jubula.client.internal.impl.ObjectMappingImpl;
import org.eclipse.jubula.client.internal.impl.ProfileFactoryImpl;
import org.eclipse.jubula.tools.ComponentIdentifier;

/**
 * @author BREDEX GmbH
 */
public final class MakeR {
    /** Constructor */
    private MakeR() {
        // hide
    }

    /**
     * @param hostname
     *            the hosts name the remote AUT-Agent is running on
     * @param port
     *            the port number the remote AUT-Agent is running on
     * @return a new AUT-Agent instance - note: currently the underlying
     *         implementation only supports <b>ONE</b> connection at a time to a
     *         remotely running AUT-Agent; multiple connections may only be
     *         established sequentially!
     */
    public static AUTAgent createAUTAgent(String hostname, int port) {
        return new AUTAgentImpl(hostname, port);
    }

    /**
     * @return a new Object Mapping instance
     * @param input
     *            an input stream providing the exported object mapping
     *            properties
     */
    public static ObjectMapping createObjectMapping(InputStream input) {
        return new ObjectMappingImpl(input);
    }

    /**
     * @param <T>
     *            the type of component identifier
     * @param componentIdentifier
     *            the component identifier string
     * @return a component identifier instance or <code>null</code>.
     * @throws IllegalArgumentException
     *             if the given string does not belong to an encoded component
     *             identifier
     */
    @Nullable
    public static <T> ComponentIdentifier<T> createCI(
            @NonNull String componentIdentifier) 
            throws IllegalArgumentException {
        ComponentIdentifier<T> identifier = null;
        try {
            identifier = ObjectMappingImpl.getIdentifier(componentIdentifier);
        } catch (LoadResourceException ex) {
            throw new IllegalArgumentException(ex);
        }
        return identifier;
    }
    
    /**
     * Get the factory for heuristic profiles
     * @return the profile factory instance
     * @since 3.2
     */
    public static ProfileFactory getProfileFactory() {
        return ProfileFactoryImpl.INSTANCE;
    }
}