/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.schema.impl;


import ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.CallImplementor;
import ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.NullPolicy;
import ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.ReflectiveCallNotNullImplementor;
import ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.RexImpTable;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.schema.ImplementableFunction;
import ch.unibas.dmi.dbis.polyphenydb.schema.ScalarFunction;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperatorBinding;
import ch.unibas.dmi.dbis.polyphenydb.util.Static;
import com.google.common.collect.ImmutableMultimap;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.calcite.linq4j.function.SemiStrict;
import org.apache.calcite.linq4j.function.Strict;


/**
 * Implementation of {@link ch.unibas.dmi.dbis.polyphenydb.schema.ScalarFunction}.
 */
public class ScalarFunctionImpl extends ReflectiveFunctionBase implements ScalarFunction, ImplementableFunction {

    private final CallImplementor implementor;


    /**
     * Private constructor.
     */
    private ScalarFunctionImpl( Method method, CallImplementor implementor ) {
        super( method );
        this.implementor = implementor;
    }


    /**
     * Creates {@link ch.unibas.dmi.dbis.polyphenydb.schema.ScalarFunction} for each method in a given class.
     */
    public static ImmutableMultimap<String, ScalarFunction> createAll( Class<?> clazz ) {
        final ImmutableMultimap.Builder<String, ScalarFunction> builder = ImmutableMultimap.builder();
        for ( Method method : clazz.getMethods() ) {
            if ( method.getDeclaringClass() == Object.class ) {
                continue;
            }
            if ( !Modifier.isStatic( method.getModifiers() ) && !classHasPublicZeroArgsConstructor( clazz ) ) {
                continue;
            }
            final ScalarFunction function = create( method );
            builder.put( method.getName(), function );
        }
        return builder.build();
    }


    /**
     * Creates {@link ch.unibas.dmi.dbis.polyphenydb.schema.ScalarFunction} from given class.
     *
     * If a method of the given name is not found or it does not suit, returns {@code null}.
     *
     * @param clazz class that is used to implement the function
     * @param methodName Method name (typically "eval")
     * @return created {@link ScalarFunction} or null
     */
    public static ScalarFunction create( Class<?> clazz, String methodName ) {
        final Method method = findMethod( clazz, methodName );
        if ( method == null ) {
            return null;
        }
        return create( method );
    }


    /**
     * Creates {@link ch.unibas.dmi.dbis.polyphenydb.schema.ScalarFunction} from given method.
     * When {@code eval} method does not suit, {@code null} is returned.
     *
     * @param method method that is used to implement the function
     * @return created {@link ScalarFunction} or null
     */
    public static ScalarFunction create( Method method ) {
        if ( !Modifier.isStatic( method.getModifiers() ) ) {
            Class clazz = method.getDeclaringClass();
            if ( !classHasPublicZeroArgsConstructor( clazz ) ) {
                throw Static.RESOURCE.requireDefaultConstructor( clazz.getName() ).ex();
            }
        }
        CallImplementor implementor = createImplementor( method );
        return new ScalarFunctionImpl( method, implementor );
    }


    public RelDataType getReturnType( RelDataTypeFactory typeFactory ) {
        return typeFactory.createJavaType( method.getReturnType() );
    }


    public CallImplementor getImplementor() {
        return implementor;
    }


    private static CallImplementor createImplementor( final Method method ) {
        final NullPolicy nullPolicy = getNullPolicy( method );
        return RexImpTable.createImplementor( new ReflectiveCallNotNullImplementor( method ), nullPolicy, false );
    }


    private static NullPolicy getNullPolicy( Method m ) {
        if ( m.getAnnotation( Strict.class ) != null ) {
            return NullPolicy.STRICT;
        } else if ( m.getAnnotation( SemiStrict.class ) != null ) {
            return NullPolicy.SEMI_STRICT;
        } else if ( m.getDeclaringClass().getAnnotation( Strict.class ) != null ) {
            return NullPolicy.STRICT;
        } else if ( m.getDeclaringClass().getAnnotation( SemiStrict.class ) != null ) {
            return NullPolicy.SEMI_STRICT;
        } else {
            return NullPolicy.NONE;
        }
    }


    public RelDataType getReturnType( RelDataTypeFactory typeFactory, SqlOperatorBinding opBinding ) {
        // Strict and semi-strict functions can return null even if their Java functions return a primitive type. Because when one of their arguments
        // is null, they won't even be called.
        final RelDataType returnType = getReturnType( typeFactory );
        switch ( getNullPolicy( method ) ) {
            case STRICT:
                for ( RelDataType type : opBinding.collectOperandTypes() ) {
                    if ( type.isNullable() ) {
                        return typeFactory.createTypeWithNullability( returnType, true );
                    }
                }
                break;
            case SEMI_STRICT:
                return typeFactory.createTypeWithNullability( returnType, true );
        }
        return returnType;
    }
}

