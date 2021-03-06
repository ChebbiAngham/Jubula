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
package org.eclipse.jubula.client.core.model;

import javax.persistence.CascadeType;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.eclipse.jubula.client.core.businessprocess.IParamNameMapper;
import org.eclipse.jubula.client.core.businessprocess.ParamNameBP;


/**
 * @author BREDEX GmbH
 * @created 29.06.2007
 */
@Entity
@DiscriminatorValue(value = "T")
class TcParamDescriptionPO extends ParamDescriptionPO implements 
    ITcParamDescriptionPO {
    
    /**
     * <code>m_mapper</code>mapper for resolving of param names
     */
    private IParamNameMapper m_mapper = null;
    /** the {@link IParamValueSetPO} for restricting values for a parameter */
    private IParamValueSetPO m_paramValueSet = null;

    /**
     * only for Persistence (JPA / EclipseLink)
     */
    TcParamDescriptionPO() {
        // nothing
    }
    
    /**
     * @param type parameter type
     * @param uniqueId I18NKey for parameter
     * @param mapper mapper to resolve param names
     */
    TcParamDescriptionPO(String type, String uniqueId, 
        IParamNameMapper mapper) {
        super(type, uniqueId);
        setParamNameMapper(mapper);
    }

    /**
     * {@inheritDoc}
     */
    @Transient
    public String getName() {
        return getParamNameMapper().getName(getUniqueId(), 
            getParentProjectId());
    }
    
    /**
     * @param mapper associates ParamNameMapper
     * important for resolving of parameter names 
     */
    public void setParamNameMapper(IParamNameMapper mapper) {
        m_mapper = mapper;
    }

    /**
     * @return the mapper
     */
    @Transient
    IParamNameMapper getParamNameMapper() {
        if (m_mapper == null) {
            m_mapper = ParamNameBP.getInstance();
        }
        return m_mapper;
    }

    /**
     * {@inheritDoc}
     */
    @OneToOne(targetEntity = ParamValueSetPO.class, cascade = CascadeType.ALL)
    @JoinColumn(name = "VALUE_SET_ID")
    protected IParamValueSetPO getPersistenceValueSet() {
        return m_paramValueSet;
    }
    
    /**
     * {@inheritDoc}
     */
    @Transient
    public IParamValueSetPO getValueSet() {
        if (m_paramValueSet == null) {
            m_paramValueSet = new ParamValueSetPO();
            m_paramValueSet.setParentProjectId(getParentProjectId());
        }
        return m_paramValueSet;
    }
    /**
     * persistence method
     * @param valueSet the value set
     */
    void setPersistenceValueSet(IParamValueSetPO valueSet) {
        m_paramValueSet = valueSet;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void setValueSet(IParamValueSetPO valueSet) {
        setPersistenceValueSet(valueSet);
    }

}
