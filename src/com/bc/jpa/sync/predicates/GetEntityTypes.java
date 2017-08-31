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

package com.bc.jpa.sync.predicates;

import com.bc.jpa.JpaMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Chinomso Bassey Ikwuagwu on Jul 29, 2017 11:12:12 AM
 */
public class GetEntityTypes implements Function<JpaMetaData, List<Class>> {

    private final Predicate<String> persistenceUnitNameTest;

    public GetEntityTypes(Predicate<String> persistenceUnitNameTest) {
        this.persistenceUnitNameTest = Objects.requireNonNull(persistenceUnitNameTest);
    }
    
    @Override
    public List<Class> apply(JpaMetaData metaData) {
        final List<Class> output = new ArrayList();
        final String [] puNames = metaData.getPersistenceUnitNames();
        for(String puName : puNames) {
            if(persistenceUnitNameTest.test(puName)) {
                final Class [] puClasses = metaData.getEntityClasses(puName); 
                output.addAll(Arrays.asList(puClasses));
            }
        }
//        logger.log(Level.FINE, "Master types: {0}", masterTypes);
        return output;
    }
}
