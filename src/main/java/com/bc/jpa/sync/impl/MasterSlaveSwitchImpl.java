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

import com.bc.jpa.sync.MasterSlaveSwitch;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author Chinomso Bassey Ikwuagwu on Nov 21, 2017 3:23:56 PM
 */
public class MasterSlaveSwitchImpl<T> implements Serializable, MasterSlaveSwitch<T> {

    private final Predicate<T> masterTest; 
    private final Predicate<T> slaveTest;
    
    private final T master;
    private final T slave;
    private T active;

    public MasterSlaveSwitchImpl(
            Set<T> values, Predicate<T> masterTest, Predicate<T> slaveTest) {
        this.masterTest = Objects.requireNonNull(masterTest);
        this.slaveTest = Objects.requireNonNull(slaveTest);
        this.master = values.stream().filter(masterTest).findFirst()
                .orElseThrow(() -> new IllegalArgumentException());
        this.slave = values.stream().filter(slaveTest).findFirst().orElse(null);
    }
    
    @Override
    public T toggle() {
        if(Objects.equals(this.active, this.master)) {
            return this.switchToSlave();
        }else if(Objects.equals(this.active, this.slave)) {
            return this.switchToMaster();
        }else{
            return null;
        }
    }
    
    @Override
    public T switchToMaster() {
        return (this.active = this.master);
    }
    
    @Override
    public T switchToSlave() {
        return (this.active = this.slave);
    }

    @Override
    public T getActive() {
        return this.active;
    }

    @Override
    public Predicate<T> getMasterTest() {
        return masterTest;
    }

    @Override
    public Predicate<T> getSlaveTest() {
        return slaveTest;
    }

    @Override
    public T getMaster() {
        return master;
    }

    @Override
    public Optional<T> getSlaveOptional() {
        return Optional.ofNullable(slave);
    }
}
