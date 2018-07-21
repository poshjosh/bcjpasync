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

package com.bc.jpa.sync;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author Chinomso Bassey Ikwuagwu on Nov 21, 2017 3:49:38 PM
 */
public interface MasterSlaveSwitch<T> {

    default boolean isMasterActive() {
        return this.isActive(this.getMaster());
    }

    default boolean isSlaveActive() {
        final Optional<T> optSlave = this.getSlaveOptional();
        return !optSlave.isPresent() ? false : this.isActive(optSlave.get());
    }
    
    default boolean isActive(T instance) {
        return this.getActive().equals(instance);
    }
    
    T getActive();

    T getMaster();

    Predicate<T> getMasterTest();

    Optional<T> getSlaveOptional();

    Predicate<T> getSlaveTest();

    T switchToMaster();

    T switchToSlave();

    T toggle();
}
