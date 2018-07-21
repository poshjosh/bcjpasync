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

import com.bc.jpa.sync.MasterSlavePersistenceContext;
import com.bc.jpa.context.PersistenceUnitContext;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import com.bc.jpa.EntityMemberAccess;
import com.bc.jpa.functions.RemoveManyToOneReferences;
import java.util.Optional;

/**
 * @author Chinomso Bassey Ikwuagwu on Oct 30, 2017 10:13:35 PM
 */
public class MasterSlavePersistenceContextImpl implements  Serializable, MasterSlavePersistenceContext {

    private transient static final Logger logger = 
            Logger.getLogger(MasterSlavePersistenceContextImpl.class.getName());

    private final PersistenceUnitContext master;
    private final PersistenceUnitContext slave;
    
    private final Map<Class, EntityMemberAccess> masterUpdaters;
    private final Map<Class, EntityMemberAccess> slaveUpdaters;
    
    private final Function preFormatter;
    private final Function postFormatter;

    public MasterSlavePersistenceContextImpl(
            PersistenceUnitContext masterContext, 
            PersistenceUnitContext slaveContext) {
        this(
                masterContext, 
                slaveContext, 
                new RemoveManyToOneReferences(masterContext.getEntityReference()), 
                (e) -> { return e; }
        );
    }
    
    public MasterSlavePersistenceContextImpl(
            PersistenceUnitContext masterContext, 
            PersistenceUnitContext slaveContext,
            Function preFormatter, Function postFormatter) {
        this.master = Objects.requireNonNull(masterContext);
        this.slave = Objects.requireNonNull(slaveContext);
        this.preFormatter = Objects.requireNonNull(preFormatter);
        this.postFormatter = Objects.requireNonNull(postFormatter);
        this.masterUpdaters = new LinkedHashMap<>();
        this.slaveUpdaters = new LinkedHashMap<>();
    }
    
    @Override
    public Object apply(Object masterEntity) {
        
        final Set<Class> masterTypes = this.master.getMetaData().getEntityClasses();
        
        if(!masterTypes.contains(masterEntity.getClass())) {
            throw new UnsupportedOperationException(
                    "Not a master type: "+masterEntity.getClass()+", instance: "+masterEntity);
        }
        
        final Object slaveEntity = this.toSlave(masterEntity, null);
        
        logger.finer(() -> "#getSlave(..) Master: "+masterEntity+", slave: "+slaveEntity);
        
        Objects.requireNonNull(slaveEntity);
        
        return slaveEntity;
    }
    
    @Override
    public Object toSlave(Object masterEntity, Object outputIfNone) {
       
        final Set<Class> slaveTypes = slave.getMetaData().getEntityClasses();

        Object output = outputIfNone;
        
        final Class masterType = masterEntity.getClass();
        
        final Class slaveType = slaveTypes.contains(masterType) ? masterEntity.getClass() :
                this.getOtherTypeFromContext(slave, masterType, null);
        
        if(slaveType != null) {
            
            masterEntity = this.preFormatter.apply(masterEntity);
            
            output = this.createSlave(masterEntity, masterType, slaveType);
            
            output = this.postFormatter.apply(output);
        }
        
//        System.out.println("Master: " + masterEntity + ", slave: " + output + ". @"+this.getClass());
        return output;
    }
    
    public Class getOtherTypeFromContext(PersistenceUnitContext context, Class type, Class outputIfNone) {

        Class output = outputIfNone;
        final Set<Class> classes = context.getMetaData().getEntityClasses();
        
        if(classes.contains(type)) {
            output = type;
        }else{
            for(Class aClass : classes) {
                if(aClass.getSimpleName().equals(type.getSimpleName())) {
                    output = aClass;
                    break;
                }
            }
        }
        return output;
    }
    
    public Object createSlave(Object masterEntity, Class masterType, Class slaveType) {

        final Object slaveEntity = this.newInstance(slaveType);
        
        logger.finer(() -> "Source: " + masterEntity + ", empty target: " + slaveEntity);

        final EntityMemberAccess masterUpdater = this.getMasterUpdater(masterType);
        final EntityMemberAccess slaveUpdater = this.getSlaveUpdater(slaveType);
        final String [] columnNames = this.slave.getMetaData().getColumnNames(slaveType);
        
        logger.finer(() -> "SLAVE Column names: " + Arrays.toString(columnNames));
        
        for(String columnName : columnNames) {
            
            final Object masterValue = masterUpdater.getValue(masterEntity, columnName);
            
            logger.finer(() -> "Getting: " + masterEntity.getClass().getName() + '#' + columnName + '=' + masterValue);                    
            
//            System.out.println("Getting: " + masterEntity.getClass().getName() + '#' + columnName + '=' + masterValue);
            
            final Object slaveValue;
            if(masterValue == null) {
                slaveValue = masterValue;
            }else if(masterEntity.equals(masterValue)) {
                slaveValue = slaveEntity;
            }else if(masterValue instanceof Collection) {
                final Collection masterCollection = (Collection)masterValue;
                final Collection slaveCollection = (Collection)this.newInstance(masterCollection.getClass());
                for(Object e : masterCollection) {
                    final Object slaveE = this.toSlave(e, e);
                    slaveCollection.add(slaveE);
                }
                slaveValue = slaveCollection;
            }else{
                slaveValue = this.toSlave(masterValue, masterValue);
            }
            
//            if("Appointment".equals(slaveEntity.getClass().getSimpleName()) && "unit".equals(columnName)) {
//                System.out.println("Setting: " + slaveEntity.getClass().getName() + '#' + columnName + '=' + slaveValue);                                    
//            }
            
            logger.finer(() -> "Setting: " + slaveEntity.getClass().getName() + '#' + columnName + '=' + slaveValue);                    

            slaveUpdater.setValue(slaveEntity, columnName, slaveValue);
        }
        
        return slaveEntity;
    }
    
    private Object newInstance(Class aClass) {
        try{
            final Object output = aClass.getConstructor().newInstance();
            return output;
        }catch(NoSuchMethodException | SecurityException | InstantiationException | 
                IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
    
    public EntityMemberAccess getMasterUpdater(Class entityType) {
        return this.getUpdater(entityType, master, masterUpdaters);
    }
    
    public EntityMemberAccess getSlaveUpdater(Class entityType) {
        return this.getUpdater(entityType, slave, slaveUpdaters);
    }

    private EntityMemberAccess getUpdater(Class entityType, 
            PersistenceUnitContext puContext, Map<Class, EntityMemberAccess> cache) {
        EntityMemberAccess updater = cache.get(entityType);
        if(updater == null) {
            updater = puContext.getEntityMemberAccess(entityType);
            cache.put(entityType, updater);
        }
        return updater;
    }

    @Override
    public final PersistenceUnitContext getMaster() {
        return master;
    }

    @Override
    public final Optional<PersistenceUnitContext> getSlaveOptional() {
        return Optional.ofNullable(slave);
    }
}
