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

package ch.unibas.dmi.dbis.polyphenydb.interpreter;


import ch.unibas.dmi.dbis.polyphenydb.rel.RelFieldCollation;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Sort;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexLiteral;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * Interpreter node that implements a {@link Sort}.
 */
public class SortNode extends AbstractSingleNode<Sort> {

    public SortNode( Compiler compiler, Sort rel ) {
        super( compiler, rel );
    }


    public void run() throws InterruptedException {
        final int offset =
                rel.offset == null
                        ? 0
                        : ((RexLiteral) rel.offset).getValueAs( Integer.class );
        final int fetch =
                rel.fetch == null
                        ? -1
                        : ((RexLiteral) rel.fetch).getValueAs( Integer.class );
        // In pure limit mode. No sort required.
        Row row;
        loop:
        if ( rel.getCollation().getFieldCollations().isEmpty() ) {
            for ( int i = 0; i < offset; i++ ) {
                row = source.receive();
                if ( row == null ) {
                    break loop;
                }
            }
            if ( fetch >= 0 ) {
                for ( int i = 0; i < fetch && (row = source.receive()) != null; i++ ) {
                    sink.send( row );
                }
            } else {
                while ( (row = source.receive()) != null ) {
                    sink.send( row );
                }
            }
        } else {
            // Build a sorted collection.
            final List<Row> list = new ArrayList<>();
            while ( (row = source.receive()) != null ) {
                list.add( row );
            }
            list.sort( comparator() );
            final int end = fetch < 0 || offset + fetch > list.size()
                    ? list.size()
                    : offset + fetch;
            for ( int i = offset; i < end; i++ ) {
                sink.send( list.get( i ) );
            }
        }
        sink.end();
    }


    private Comparator<Row> comparator() {
        if ( rel.getCollation().getFieldCollations().size() == 1 ) {
            return comparator( rel.getCollation().getFieldCollations().get( 0 ) );
        }
        return Ordering.compound( Iterables.transform( rel.getCollation().getFieldCollations(), this::comparator ) );
    }


    private Comparator<Row> comparator( RelFieldCollation fieldCollation ) {
        final int nullComparison = fieldCollation.nullDirection.nullComparison;
        final int x = fieldCollation.getFieldIndex();
        switch ( fieldCollation.direction ) {
            case ASCENDING:
                return ( o1, o2 ) -> {
                    final Comparable c1 = (Comparable) o1.getValues()[x];
                    final Comparable c2 = (Comparable) o2.getValues()[x];
                    return RelFieldCollation.compare( c1, c2, nullComparison );
                };
            default:
                return ( o1, o2 ) -> {
                    final Comparable c1 = (Comparable) o1.getValues()[x];
                    final Comparable c2 = (Comparable) o2.getValues()[x];
                    return RelFieldCollation.compare( c2, c1, -nullComparison );
                };
        }
    }
}
