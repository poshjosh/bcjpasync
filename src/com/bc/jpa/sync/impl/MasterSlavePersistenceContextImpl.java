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
import com.bc.jpa.EntityUpdater;
import com.bc.jpa.context.PersistenceUnitContext;
import com.bc.jpa.util.JpaUtil;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Chinomso Bassey Ikwuagwu on Oct 30, 2017 10:13:35 PM
 */
public class MasterSlavePersistenceContextImpl implements  Serializable, MasterSlavePersistenceContext {

    private transient static final Logger logger = Logger.getLogger(MasterSlavePersistenceContextImpl.class.getName());

    private final PersistenceUnitContext master;
    private final PersistenceUnitContext slave;
    
    private final Map<Class, EntityUpdater> masterUpdaters;
    private final Map<Class, EntityUpdater> slaveUpdaters;
    
    public MasterSlavePersistenceContextImpl(PersistenceUnitContext masterContext, PersistenceUnitContext slaveContext) {
        this.master = Objects.requireNonNull(masterContext);
        this.slave = Objects.requireNonNull(slaveContext);
        this.masterUpdaters = new LinkedHashMap<>();
        this.slaveUpdaters = new LinkedHashMap<>();
    }
    
    @Override
    public Object apply(Object masterEntity) {
        
        final Set<Class> masterTypes = this.master.getMetaData().getEntityClasses();
        
        if(!masterTypes.contains(masterEntity.getClass())) {
            throw new UnsupportedOperationException("Not a master type: "+masterEntity.getClass()+", instance: "+masterEntity);
        }
        
        final Object slaveEntity = this.toSlave(masterEntity, null);
        
        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "#getSlave(..) Master: {0}, slave: {1}", 
                    new Object[]{masterEntity, slaveEntity});
        }
        
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
            output = this.createSlave(masterEntity, masterType, slaveType);
        }
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
        
        logger.finer(() -> "Source: " + masterEntity + ", target: " + slaveEntity);

        final EntityUpdater masterUpdater = this.getMasterUpdater(masterType);
        final EntityUpdater slaveUpdater = this.getSlaveUpdater(slaveType);
        final String [] columnNames = this.slave.getMetaData().getColumnNames(slaveType);
        
        logger.finer(() -> "SLAVE Column names: " + Arrays.toString(columnNames));
        
        this.removeManyToOnes(masterEntity);
        
        for(String columnName : columnNames) {
            
            final Object masterValue = masterUpdater.getValue(masterEntity, columnName);
            final boolean selfReference = masterEntity.equals(masterValue);
            final Object slaveValue;
            if(masterValue == null) {
                slaveValue = masterValue;
            }else if(selfReference) {
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
            
            logger.finer(() -> "Setting: " + slaveEntity.getClass().getName() + '#' + columnName + '=' + slaveValue);                    

            slaveUpdater.setValue(slaveEntity, columnName, slaveValue);
        }
        
        return slaveEntity;
    }
    
    private void removeManyToOnes(Object entity) {
        final Class refClass = entity.getClass();

        final Class [] refingClasses = master.getPersistenceContext().getEntityReference().getReferencingClasses(refClass);
        if(refingClasses != null && refingClasses.length != 0) {
            for(Class refingClass : refingClasses) {
                final Method setter = JpaUtil.getMethod(true, refClass, refingClass);
                if(setter == null) {
                    continue;
                }
                try{
                    setter.invoke(entity, new Object[]{null});
                }catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    logger.log(Level.WARNING, "Error invoking "+setter.getName()+" with argument: null", e);
                }
            }
        }
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
    
    public EntityUpdater getMasterUpdater(Class entityType) {
        return this.getUpdater(entityType, master, masterUpdaters);
    }
    
    public EntityUpdater getSlaveUpdater(Class entityType) {
        return this.getUpdater(entityType, slave, slaveUpdaters);
    }

    private EntityUpdater getUpdater(Class entityType, 
            PersistenceUnitContext puContext, Map<Class, EntityUpdater> cache) {
        EntityUpdater updater = cache.get(entityType);
        if(updater == null) {
            updater = puContext.getEntityUpdater(entityType);
            cache.put(entityType, updater);
        }
        return updater;
    }

    @Override
    public final PersistenceUnitContext getMaster() {
        return master;
    }

    @Override
    public final PersistenceUnitContext getSlave() {
        return slave;
    }
}
