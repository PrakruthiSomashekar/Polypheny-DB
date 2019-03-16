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

package ch.unibas.dmi.dbis.polyphenydb.rel.logical;


import ch.unibas.dmi.dbis.polyphenydb.plan.Convention;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitSet;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelInput;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelShuttle;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Minus;
import java.util.List;


/**
 * Sub-class of {@link Minus} not targeted at any particular engine or calling convention.
 */
public final class LogicalMinus extends Minus {

    /**
     * Creates a LogicalMinus.
     *
     * Use {@link #create} unless you know what you're doing.
     */
    public LogicalMinus( RelOptCluster cluster, RelTraitSet traitSet, List<RelNode> inputs, boolean all ) {
        super( cluster, traitSet, inputs, all );
    }


    @Deprecated // to be removed before 2.0
    public LogicalMinus( RelOptCluster cluster, List<RelNode> inputs, boolean all ) {
        this( cluster, cluster.traitSetOf( Convention.NONE ), inputs, all );
    }


    /**
     * Creates a LogicalMinus by parsing serialized output.
     */
    public LogicalMinus( RelInput input ) {
        super( input );
    }


    /**
     * Creates a LogicalMinus.
     */
    public static LogicalMinus create( List<RelNode> inputs, boolean all ) {
        final RelOptCluster cluster = inputs.get( 0 ).getCluster();
        final RelTraitSet traitSet = cluster.traitSetOf( Convention.NONE );
        return new LogicalMinus( cluster, traitSet, inputs, all );
    }


    @Override
    public LogicalMinus copy( RelTraitSet traitSet, List<RelNode> inputs, boolean all ) {
        assert traitSet.containsIfApplicable( Convention.NONE );
        return new LogicalMinus( getCluster(), traitSet, inputs, all );
    }


    @Override
    public RelNode accept( RelShuttle shuttle ) {
        return shuttle.visit( this );
    }
}

