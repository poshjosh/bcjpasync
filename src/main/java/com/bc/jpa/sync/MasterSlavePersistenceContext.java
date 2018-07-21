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

import com.bc.jpa.context.PersistenceUnitContext;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author Chinomso Bassey Ikwuagwu on Oct 30, 2017 10:27:18 PM
 */
public interface MasterSlavePersistenceContext extends Function {

    PersistenceUnitContext getMaster();

    Optional<PersistenceUnitContext> getSlaveOptional();
    
    @Override
    Object apply(Object master);

    Object toSlave(Object master, Object outputIfNone);
}
