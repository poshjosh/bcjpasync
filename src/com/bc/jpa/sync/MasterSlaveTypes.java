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

import java.util.List;
import java.util.function.Predicate;

/**
 * @author Chinomso Bassey Ikwuagwu on Aug 7, 2017 8:43:23 PM
 */
public interface MasterSlaveTypes {
    
    Class getAlternateType(Class type, Class outputIfNone);
    
    Predicate<String> getMasterPersistenceUnitTest();

    Class getMasterType(Class type, Class outputIfNone);

    List<Class> getMasterTypes();
    
    Predicate<String> getSlavePersistenceUnitTest();

    Class getSlaveType(Class type, Class outputIfNone);

    List<Class> getSlaveTypes();
    
    boolean isMasterType(Class type);
    
    boolean isSlaveType(Class type);
}
