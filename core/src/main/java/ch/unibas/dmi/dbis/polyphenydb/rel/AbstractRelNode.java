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

package ch.unibas.dmi.dbis.polyphenydb.rel;


import ch.unibas.dmi.dbis.polyphenydb.plan.Convention;
import ch.unibas.dmi.dbis.polyphenydb.plan.ConventionTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCost;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptPlanner;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptQuery;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptTable;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptUtil;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTrait;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitSet;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.CorrelationId;
import ch.unibas.dmi.dbis.polyphenydb.rel.externalize.RelWriterImpl;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.Metadata;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.MetadataFactory;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMetadataQuery;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexShuttle;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlExplainLevel;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableBitSet;
import ch.unibas.dmi.dbis.polyphenydb.util.Litmus;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import ch.unibas.dmi.dbis.polyphenydb.util.trace.PolyphenyDbTrace;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;


/**
 * Base class for every relational expression ({@link RelNode}).
 */
public abstract class AbstractRelNode implements RelNode {

    /**
     * Generator for {@link #id} values.
     */
    private static final AtomicInteger NEXT_ID = new AtomicInteger( 0 );

    private static final Logger LOGGER = PolyphenyDbTrace.getPlannerTracer();


    /**
     * Description, consists of id plus digest.
     */
    private String desc;

    /**
     * Cached type of this relational expression.
     */
    protected RelDataType rowType;

    /**
     * A short description of this relational expression's type, inputs, and other properties. The string uniquely identifies the node; another node is equivalent if and only if it has the same value.
     * Computed by {@link #computeDigest}, assigned by {@link #onRegister}, returned by {@link #getDigest()}.
     *
     * @see #desc
     */
    protected String digest;

    private final RelOptCluster cluster;

    /**
     * unique id of this object -- for debugging
     */
    protected final int id;

    /**
     * The RelTraitSet that describes the traits of this RelNode.
     */
    protected RelTraitSet traitSet;


    /**
     * Creates an <code>AbstractRelNode</code>.
     */
    public AbstractRelNode( RelOptCluster cluster, RelTraitSet traitSet ) {
        super();
        assert cluster != null;
        this.cluster = cluster;
        this.traitSet = traitSet;
        this.id = NEXT_ID.getAndIncrement();
        this.digest = getRelTypeName() + "#" + id;
        this.desc = digest;
        LOGGER.trace( "new {}", digest );
    }


    public RelNode copy( RelTraitSet traitSet, List<RelNode> inputs ) {
        // Note that empty set equals empty set, so relational expressions with zero inputs do not generally need to implement their own copy method.
        if ( getInputs().equals( inputs ) && traitSet == getTraitSet() ) {
            return this;
        }
        throw new AssertionError( "Relational expression should override copy. Class=[" + getClass() + "]; traits=[" + getTraitSet() + "]; desired traits=[" + traitSet + "]" );
    }


    protected static <T> T sole( List<T> collection ) {
        assert collection.size() == 1;
        return collection.get( 0 );
    }


    @SuppressWarnings("deprecation")
    public List<RexNode> getChildExps() {
        return ImmutableList.of();
    }


    public final RelOptCluster getCluster() {
        return cluster;
    }


    public final Convention getConvention() {
        return traitSet.getTrait( ConventionTraitDef.INSTANCE );
    }


    public RelTraitSet getTraitSet() {
        return traitSet;
    }


    public String getCorrelVariable() {
        return null;
    }


    @SuppressWarnings("deprecation")
    public boolean isDistinct() {
        final RelMetadataQuery mq = RelMetadataQuery.instance();
        return Boolean.TRUE.equals( mq.areRowsUnique( this ) );
    }


    @SuppressWarnings("deprecation")
    public boolean isKey( ImmutableBitSet columns ) {
        final RelMetadataQuery mq = RelMetadataQuery.instance();
        return Boolean.TRUE.equals( mq.areColumnsUnique( this, columns ) );
    }


    public int getId() {
        return id;
    }


    public RelNode getInput( int i ) {
        List<RelNode> inputs = getInputs();
        return inputs.get( i );
    }


    @SuppressWarnings("deprecation")
    public final RelOptQuery getQuery() {
        return getCluster().getQuery();
    }


    public void register( RelOptPlanner planner ) {
        Util.discard( planner );
    }


    public final String getRelTypeName() {
        String className = getClass().getName();
        int i = className.lastIndexOf( "$" );
        if ( i >= 0 ) {
            return className.substring( i + 1 );
        }
        i = className.lastIndexOf( "." );
        if ( i >= 0 ) {
            return className.substring( i + 1 );
        }
        return className;
    }


    public boolean isValid( Litmus litmus, Context context ) {
        return litmus.succeed();
    }


    @SuppressWarnings("deprecation")
    public boolean isValid( boolean fail ) {
        return isValid( Litmus.THROW, null );
    }


    /**
     * @deprecated Use {@link RelMetadataQuery#collations(RelNode)}
     */
    @Deprecated // to be removed before 2.0
    public List<RelCollation> getCollationList() {
        return ImmutableList.of();
    }


    public final RelDataType getRowType() {
        if ( rowType == null ) {
            rowType = deriveRowType();
            assert rowType != null : this;
        }
        return rowType;
    }


    protected RelDataType deriveRowType() {
        // This method is only called if rowType is null, so you don't NEED to implement it if rowType is always set.
        throw new UnsupportedOperationException();
    }


    public RelDataType getExpectedInputRowType( int ordinalInParent ) {
        return getRowType();
    }


    public List<RelNode> getInputs() {
        return Collections.emptyList();
    }


    @SuppressWarnings("deprecation")
    public final double getRows() {
        return estimateRowCount( RelMetadataQuery.instance() );
    }


    public double estimateRowCount( RelMetadataQuery mq ) {
        return 1.0;
    }


    @SuppressWarnings("deprecation")
    public final Set<String> getVariablesStopped() {
        return CorrelationId.names( getVariablesSet() );
    }


    public Set<CorrelationId> getVariablesSet() {
        return ImmutableSet.of();
    }


    public void collectVariablesUsed( Set<CorrelationId> variableSet ) {
        // for default case, nothing to do
    }


    public void collectVariablesSet( Set<CorrelationId> variableSet ) {
    }


    public void childrenAccept( RelVisitor visitor ) {
        List<RelNode> inputs = getInputs();
        for ( int i = 0; i < inputs.size(); i++ ) {
            visitor.visit( inputs.get( i ), i, this );
        }
    }


    public RelNode accept( RelShuttle shuttle ) {
        // Call fall-back method. Specific logical types (such as LogicalProject and LogicalJoin) have their own RelShuttle.visit methods.
        return shuttle.visit( this );
    }


    public RelNode accept( RexShuttle shuttle ) {
        return this;
    }


    @SuppressWarnings("deprecation")
    public final RelOptCost computeSelfCost( RelOptPlanner planner ) {
        return computeSelfCost( planner, RelMetadataQuery.instance() );
    }


    public RelOptCost computeSelfCost( RelOptPlanner planner, RelMetadataQuery mq ) {
        // by default, assume cost is proportional to number of rows
        double rowCount = mq.getRowCount( this );
        double bytesPerRow = 1;
        return planner.getCostFactory().makeCost( rowCount, rowCount, 0 );
    }


    public final <M extends Metadata> M metadata( Class<M> metadataClass, RelMetadataQuery mq ) {
        final MetadataFactory factory = cluster.getMetadataFactory();
        final M metadata = factory.query( this, mq, metadataClass );
        assert metadata != null : "no provider found (rel=" + this + ", m=" + metadataClass + "); a backstop provider is recommended";
        // Usually the metadata belongs to the rel that created it. RelSubset and HepRelVertex are notable exceptions, so disable the assert. It's not worth the performance hit to override this method for them.
        //   assert metadata.rel() == this : "someone else's metadata";
        return metadata;
    }


    public void explain( RelWriter pw ) {
        explainTerms( pw ).done( this );
    }


    /**
     * Describes the inputs and attributes of this relational expression.
     * Each node should call {@code super.explainTerms}, then call the {@link ch.unibas.dmi.dbis.polyphenydb.rel.externalize.RelWriterImpl#input(String, RelNode)} and {@link ch.unibas.dmi.dbis.polyphenydb.rel.externalize.RelWriterImpl#item(String, Object)}
     * methods for each input and attribute.
     *
     * @param pw Plan writer
     * @return Plan writer for fluent-explain pattern
     */
    public RelWriter explainTerms( RelWriter pw ) {
        return pw;
    }


    public RelNode onRegister( RelOptPlanner planner ) {
        List<RelNode> oldInputs = getInputs();
        List<RelNode> inputs = new ArrayList<>( oldInputs.size() );
        for ( final RelNode input : oldInputs ) {
            RelNode e = planner.ensureRegistered( input, null );
            if ( e != input ) {
                // TODO: change 'equal' to 'eq', which is stronger.
                assert RelOptUtil.equal(
                        "rowtype of rel before registration",
                        input.getRowType(),
                        "rowtype of rel after registration",
                        e.getRowType(),
                        Litmus.THROW );
            }
            inputs.add( e );
        }
        RelNode r = this;
        if ( !Util.equalShallow( oldInputs, inputs ) ) {
            r = copy( getTraitSet(), inputs );
        }
        r.recomputeDigest();
        assert r.isValid( Litmus.THROW, null );
        return r;
    }


    public String recomputeDigest() {
        String tempDigest = computeDigest();
        assert tempDigest != null : "computeDigest() should be non-null";

        this.desc = "rel#" + id + ":" + tempDigest;
        this.digest = tempDigest;
        return this.digest;
    }


    public void replaceInput( int ordinalInParent, RelNode p ) {
        throw new UnsupportedOperationException( "replaceInput called on " + this );
    }


    public String toString() {
        return desc;
    }


    public final String getDescription() {
        return desc;
    }


    public final String getDigest() {
        return digest;
    }


    public RelOptTable getTable() {
        return null;
    }


    /**
     * Computes the digest. Does not modify this object.
     *
     * @return Digest
     */
    protected String computeDigest() {
        StringWriter sw = new StringWriter();
        RelWriter pw =
                new RelWriterImpl( new PrintWriter( sw ), SqlExplainLevel.DIGEST_ATTRIBUTES, false ) {
                    protected void explain_( RelNode rel, List<Pair<String, Object>> values ) {
                        pw.write( getRelTypeName() );

                        for ( RelTrait trait : traitSet ) {
                            pw.write( "." );
                            pw.write( trait.toString() );
                        }

                        pw.write( "(" );
                        int j = 0;
                        for ( Pair<String, Object> value : values ) {
                            if ( j++ > 0 ) {
                                pw.write( "," );
                            }
                            pw.write( value.left );
                            pw.write( "=" );
                            if ( value.right instanceof RelNode ) {
                                RelNode input = (RelNode) value.right;
                                pw.write( input.getRelTypeName() );
                                pw.write( "#" );
                                pw.write( Integer.toString( input.getId() ) );
                            } else {
                                pw.write( String.valueOf( value.right ) );
                            }
                        }
                        pw.write( ")" );
                    }
                };
        explain( pw );
        return sw.toString();
    }
}

