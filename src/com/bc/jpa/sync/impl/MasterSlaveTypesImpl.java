/*
 * Copyright 2017 NUROX Ltd.
 *
 * Licensed under the NUROX Ltd Software License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.looseboxes.com/legal/licenses/software.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bc.jpa.sync.impl;

import com.bc.jpa.sync.MasterSlaveTypes;
import com.bc.jpa.JpaMetaData;
import com.bc.jpa.sync.predicates.GetEntityTypes;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Chinomso Bassey Ikwuagwu on Aug 7, 2017 8:40:33 PM
 */
public class MasterSlaveTypesImpl implements MasterSlaveTypes {

    private static final Logger logger = Logger.getLogger(MasterSlaveTypesImpl.class.getName());

    private final Predicate<String> masterPersistenceUnitTest;
    private final Predicate<String> slavePersistenceUnitTest;
                    
    private final List<Class> masterTypes;
    private final List<Class> slaveTypes;
    
    public MasterSlaveTypesImpl(
            JpaMetaData jpaMetaData, 
            Predicate<String> persistenceUnitNameTest) {
        this(jpaMetaData, persistenceUnitNameTest, persistenceUnitNameTest);
    }
    
    public MasterSlaveTypesImpl(
            JpaMetaData jpaMetaData, 
            Predicate<String> masterPersistenceUnitFilter,
            Predicate<String> slavePersistenceUnitFilter) {
        this.masterTypes = Collections.unmodifiableList(new GetEntityTypes(masterPersistenceUnitFilter).apply(jpaMetaData));
        this.slaveTypes = Collections.unmodifiableList(new GetEntityTypes(slavePersistenceUnitFilter).apply(jpaMetaData));
        this.masterPersistenceUnitTest = Objects.requireNonNull(masterPersistenceUnitFilter);
        this.slavePersistenceUnitTest = Objects.requireNonNull(slavePersistenceUnitFilter);
        logger.log(Level.FINE, "Master types: {0}", masterTypes);
        logger.log(Level.FINE, "Slave types: {0}", slaveTypes);
    }

    @Override
    public Predicate<String> getMasterPersistenceUnitTest() {
        return masterPersistenceUnitTest;
    }

    @Override
    public Predicate<String> getSlavePersistenceUnitTest() {
        return slavePersistenceUnitTest;
    }

    @Override
    public Class getAlternateType(Class type, Class outputIfNone) {
        final Class output;
        if(this.isMasterType(type)) {
            output = this.getSlaveType(type, outputIfNone);
        }else if(this.isSlaveType(type)) {
            output = this.getMasterType(type, outputIfNone);
        }else{
            output = outputIfNone;
        }
        return output;
    }

    @Override
    public Class getSlaveType(Class type, Class outputIfNone) {
        Class output = outputIfNone;
        if(this.slaveTypes.contains(type)) {
            output = type;
        }else{
            for(Class slaveType : this.slaveTypes) {
                if(slaveType.getSimpleName().equals(type.getSimpleName())) {
                    output = slaveType;
                    break;
                }
            }
        }
        return output;
    }
    
    @Override
    public Class getMasterType(Class type, Class outputIfNone) {
        Class output = outputIfNone;
        if(this.masterTypes.contains(type)) {
            output = type;
        }else{
            for(Class masterType : this.masterTypes) {
                if(masterType.getSimpleName().equals(type.getSimpleName())) {
                    output = masterType;
                    break;
                }
            }
        }
        return output;
    }

    @Override
    public boolean isMasterType(Class type) {
        return this.masterTypes.contains(type);
    }

    @Override
    public boolean isSlaveType(Class type) {
        return this.slaveTypes.contains(type);
    }

    @Override
    public List<Class> getMasterTypes() {
        return Collections.unmodifiableList(masterTypes);
    }

    @Override
    public List<Class> getSlaveTypes() {
        return Collections.unmodifiableList(slaveTypes);
    }
}
