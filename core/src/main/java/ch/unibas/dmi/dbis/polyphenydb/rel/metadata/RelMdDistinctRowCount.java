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

package ch.unibas.dmi.dbis.polyphenydb.rel.metadata;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptUtil;
import ch.unibas.dmi.dbis.polyphenydb.plan.volcano.RelSubset;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Aggregate;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Exchange;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Filter;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Join;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Project;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.SemiJoin;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Sort;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Union;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Values;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexBuilder;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.util.Bug;
import ch.unibas.dmi.dbis.polyphenydb.util.BuiltInMethod;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableBitSet;
import ch.unibas.dmi.dbis.polyphenydb.util.NumberUtil;
import java.util.ArrayList;
import java.util.List;


/**
 * RelMdDistinctRowCount supplies a default implementation of {@link RelMetadataQuery#getDistinctRowCount} for the standard logical algebra.
 */
public class RelMdDistinctRowCount implements MetadataHandler<BuiltInMetadata.DistinctRowCount> {

    public static final RelMetadataProvider SOURCE = ReflectiveRelMetadataProvider.reflectiveSource( BuiltInMethod.DISTINCT_ROW_COUNT.method, new RelMdDistinctRowCount() );


    protected RelMdDistinctRowCount() {
    }


    public MetadataDef<BuiltInMetadata.DistinctRowCount> getDef() {
        return BuiltInMetadata.DistinctRowCount.DEF;
    }


    /**
     * Catch-all implementation for {@link BuiltInMetadata.DistinctRowCount#getDistinctRowCount(ImmutableBitSet, RexNode)}, invoked using reflection.
     *
     * @see ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMetadataQuery#getDistinctRowCount(RelNode, ImmutableBitSet, RexNode)
     */
    public Double getDistinctRowCount( RelNode rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        // REVIEW zfong 4/19/06 - Broadbase code does not take into consideration selectivity of predicates passed in.  Also, they assume the rows are unique even if the table is not
        boolean uniq = RelMdUtil.areColumnsDefinitelyUnique( mq, rel, groupKey );
        if ( uniq ) {
            return NumberUtil.multiply( mq.getRowCount( rel ), mq.getSelectivity( rel, predicate ) );
        }
        return null;
    }


    public Double getDistinctRowCount( Union rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        double rowCount = 0.0;
        int[] adjustments = new int[rel.getRowType().getFieldCount()];
        RexBuilder rexBuilder = rel.getCluster().getRexBuilder();
        for ( RelNode input : rel.getInputs() ) {
            // convert the predicate to reference the types of the union child
            RexNode modifiedPred;
            if ( predicate == null ) {
                modifiedPred = null;
            } else {
                modifiedPred =
                        predicate.accept(
                                new RelOptUtil.RexInputConverter(
                                        rexBuilder,
                                        null,
                                        input.getRowType().getFieldList(),
                                        adjustments ) );
            }
            Double partialRowCount = mq.getDistinctRowCount( input, groupKey, modifiedPred );
            if ( partialRowCount == null ) {
                return null;
            }
            rowCount += partialRowCount;
        }
        return rowCount;
    }


    public Double getDistinctRowCount( Sort rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        return mq.getDistinctRowCount( rel.getInput(), groupKey, predicate );
    }


    public Double getDistinctRowCount( Exchange rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        return mq.getDistinctRowCount( rel.getInput(), groupKey, predicate );
    }


    public Double getDistinctRowCount( Filter rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        if ( predicate == null || predicate.isAlwaysTrue() ) {
            if ( groupKey.isEmpty() ) {
                return 1D;
            }
        }
        // REVIEW zfong 4/18/06 - In the Broadbase code, duplicates are not removed from the two filter lists.  However, the code below is doing so.
        RexNode unionPreds = RelMdUtil.unionPreds( rel.getCluster().getRexBuilder(), predicate, rel.getCondition() );

        return mq.getDistinctRowCount( rel.getInput(), groupKey, unionPreds );
    }


    public Double getDistinctRowCount( Join rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        if ( predicate == null || predicate.isAlwaysTrue() ) {
            if ( groupKey.isEmpty() ) {
                return 1D;
            }
        }
        return RelMdUtil.getJoinDistinctRowCount( mq, rel, rel.getJoinType(), groupKey, predicate, false );
    }


    public Double getDistinctRowCount( SemiJoin rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        if ( predicate == null || predicate.isAlwaysTrue() ) {
            if ( groupKey.isEmpty() ) {
                return 1D;
            }
        }
        // create a RexNode representing the selectivity of the semijoin filter and pass it to getDistinctRowCount
        RexNode newPred = RelMdUtil.makeSemiJoinSelectivityRexNode( mq, rel );
        if ( predicate != null ) {
            RexBuilder rexBuilder = rel.getCluster().getRexBuilder();
            newPred = rexBuilder.makeCall( SqlStdOperatorTable.AND, newPred, predicate );
        }

        return mq.getDistinctRowCount( rel.getLeft(), groupKey, newPred );
    }


    public Double getDistinctRowCount( Aggregate rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        if ( predicate == null || predicate.isAlwaysTrue() ) {
            if ( groupKey.isEmpty() ) {
                return 1D;
            }
        }
        // determine which predicates can be applied on the child of the aggregate
        final List<RexNode> notPushable = new ArrayList<>();
        final List<RexNode> pushable = new ArrayList<>();
        RelOptUtil.splitFilters( rel.getGroupSet(), predicate, pushable, notPushable );
        final RexBuilder rexBuilder = rel.getCluster().getRexBuilder();
        RexNode childPreds = RexUtil.composeConjunction( rexBuilder, pushable, true );

        // set the bits as they correspond to the child input
        ImmutableBitSet.Builder childKey = ImmutableBitSet.builder();
        RelMdUtil.setAggChildKeys( groupKey, rel, childKey );

        Double distinctRowCount = mq.getDistinctRowCount( rel.getInput(), childKey.build(), childPreds );
        if ( distinctRowCount == null ) {
            return null;
        } else if ( notPushable.isEmpty() ) {
            return distinctRowCount;
        } else {
            RexNode preds = RexUtil.composeConjunction( rexBuilder, notPushable, true );
            return distinctRowCount * RelMdUtil.guessSelectivity( preds );
        }
    }


    public Double getDistinctRowCount( Values rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        if ( predicate == null || predicate.isAlwaysTrue() ) {
            if ( groupKey.isEmpty() ) {
                return 1D;
            }
        }
        double selectivity = RelMdUtil.guessSelectivity( predicate );

        // assume half the rows are duplicates
        double nRows = rel.estimateRowCount( mq ) / 2;
        return RelMdUtil.numDistinctVals( nRows, nRows * selectivity );
    }


    public Double getDistinctRowCount( Project rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        if ( predicate == null || predicate.isAlwaysTrue() ) {
            if ( groupKey.isEmpty() ) {
                return 1D;
            }
        }
        ImmutableBitSet.Builder baseCols = ImmutableBitSet.builder();
        ImmutableBitSet.Builder projCols = ImmutableBitSet.builder();
        List<RexNode> projExprs = rel.getProjects();
        RelMdUtil.splitCols( projExprs, groupKey, baseCols, projCols );

        final List<RexNode> notPushable = new ArrayList<>();
        final List<RexNode> pushable = new ArrayList<>();
        RelOptUtil.splitFilters( ImmutableBitSet.range( rel.getRowType().getFieldCount() ), predicate, pushable, notPushable );
        final RexBuilder rexBuilder = rel.getCluster().getRexBuilder();

        // get the distinct row count of the child input, passing in the columns and filters that only reference the child; convert the filter to reference the children projection expressions
        RexNode childPred = RexUtil.composeConjunction( rexBuilder, pushable, true );
        RexNode modifiedPred;
        if ( childPred == null ) {
            modifiedPred = null;
        } else {
            modifiedPred = RelOptUtil.pushPastProject( childPred, rel );
        }
        Double distinctRowCount = mq.getDistinctRowCount( rel.getInput(), baseCols.build(), modifiedPred );

        if ( distinctRowCount == null ) {
            return null;
        } else if ( !notPushable.isEmpty() ) {
            RexNode preds = RexUtil.composeConjunction( rexBuilder, notPushable, true );
            distinctRowCount *= RelMdUtil.guessSelectivity( preds );
        }

        // No further computation required if the projection expressions are all column references
        if ( projCols.cardinality() == 0 ) {
            return distinctRowCount;
        }

        // multiply by the cardinality of the non-child projection expressions
        for ( int bit : projCols.build() ) {
            Double subRowCount = RelMdUtil.cardOfProjExpr( mq, rel, projExprs.get( bit ) );
            if ( subRowCount == null ) {
                return null;
            }
            distinctRowCount *= subRowCount;
        }

        return RelMdUtil.numDistinctVals( distinctRowCount, mq.getRowCount( rel ) );
    }


    public Double getDistinctRowCount( RelSubset rel, RelMetadataQuery mq, ImmutableBitSet groupKey, RexNode predicate ) {
        final RelNode best = rel.getBest();
        if ( best != null ) {
            return mq.getDistinctRowCount( best, groupKey, predicate );
        }
        if ( !Bug.CALCITE_1048_FIXED ) {
            return getDistinctRowCount( (RelNode) rel, mq, groupKey, predicate );
        }
        Double d = null;
        for ( RelNode r2 : rel.getRels() ) {
            try {
                Double d2 = mq.getDistinctRowCount( r2, groupKey, predicate );
                d = NumberUtil.min( d, d2 );
            } catch ( CyclicMetadataException e ) {
                // Ignore this relational expression; there will be non-cyclic ones in this set.
            }
        }
        return d;
    }
}
