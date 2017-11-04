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

import com.bc.functions.FindExceptionInChain;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.eclipse.persistence.exceptions.DatabaseException;

/**
 * @author Chinomso Bassey Ikwuagwu on Mar 9, 2017 3:11:54 PM
 */
public class PersistenceCommunicationsLinkFailureTest implements Predicate<Throwable> {

    private final BiFunction<Throwable, Predicate<Throwable>, Optional<Throwable>> findExceptionInHeirarchy;

    public PersistenceCommunicationsLinkFailureTest() {
        
        this.findExceptionInHeirarchy = new FindExceptionInChain();
    }
    
    @Override
    public boolean test(Throwable exception) {
        
        final Predicate<Throwable> exceptionTest = (t) -> 
                t instanceof DatabaseException && ((DatabaseException)t)
                        .isCommunicationFailure();
        
        return findExceptionInHeirarchy.apply(exception, exceptionTest).isPresent();
    }
}
