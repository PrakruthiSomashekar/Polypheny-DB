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

package ch.unibas.dmi.dbis.polyphenydb.sql.validate;


import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeField;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCall;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlIdentifier;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlNode;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlNodeList;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserPos;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;
import ch.unibas.dmi.dbis.polyphenydb.util.Static;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;


/**
 * Namespace whose contents are defined by the type of an {@link SqlIdentifier identifier}.
 */
public class IdentifierNamespace extends AbstractNamespace {

    private final SqlIdentifier id;
    private final SqlValidatorScope parentScope;
    public final SqlNodeList extendList;

    /**
     * The underlying namespace. Often a {@link TableNamespace}. Set on validate.
     */
    private SqlValidatorNamespace resolvedNamespace;

    /**
     * List of monotonic expressions. Set on validate.
     */
    private List<Pair<SqlNode, SqlMonotonicity>> monotonicExprs;


    /**
     * Creates an IdentifierNamespace.
     *
     * @param validator Validator
     * @param id Identifier node (or "identifier EXTEND column-list")
     * @param extendList Extension columns, or null
     * @param enclosingNode Enclosing node
     * @param parentScope Parent scope which this namespace turns to in order to
     */
    IdentifierNamespace( SqlValidatorImpl validator, SqlIdentifier id, @Nullable SqlNodeList extendList, SqlNode enclosingNode, SqlValidatorScope parentScope ) {
        super( validator, enclosingNode );
        this.id = id;
        this.extendList = extendList;
        this.parentScope = Objects.requireNonNull( parentScope );
    }


    IdentifierNamespace( SqlValidatorImpl validator, SqlNode node, SqlNode enclosingNode, SqlValidatorScope parentScope ) {
        this( validator, split( node ).left, split( node ).right, enclosingNode, parentScope );
    }


    protected static Pair<SqlIdentifier, SqlNodeList> split( SqlNode node ) {
        switch ( node.getKind() ) {
            case EXTEND:
                final SqlCall call = (SqlCall) node;
                return Pair.of( (SqlIdentifier) call.getOperandList().get( 0 ), (SqlNodeList) call.getOperandList().get( 1 ) );
            default:
                return Pair.of( (SqlIdentifier) node, null );
        }
    }


    private SqlValidatorNamespace resolveImpl( SqlIdentifier id ) {
        final SqlNameMatcher nameMatcher = validator.catalogReader.nameMatcher();
        final SqlValidatorScope.ResolvedImpl resolved = new SqlValidatorScope.ResolvedImpl();
        final List<String> names = SqlIdentifier.toStar( id.names );
        try {
            parentScope.resolveTable( names, nameMatcher, SqlValidatorScope.Path.EMPTY, resolved );
        } catch ( CyclicDefinitionException e ) {
            if ( e.depth == 1 ) {
                throw validator.newValidationError( id, Static.RESOURCE.cyclicDefinition( id.toString(), SqlIdentifier.getString( e.path ) ) );
            } else {
                throw new CyclicDefinitionException( e.depth - 1, e.path );
            }
        }
        SqlValidatorScope.Resolve previousResolve = null;
        if ( resolved.count() == 1 ) {
            final SqlValidatorScope.Resolve resolve = previousResolve = resolved.only();
            if ( resolve.remainingNames.isEmpty() ) {
                return resolve.namespace;
            }
            // If we're not case sensitive, give an error.
            // If we're case sensitive, we'll shortly try again and give an error then.
            if ( !nameMatcher.isCaseSensitive() ) {
                throw validator.newValidationError( id, Static.RESOURCE.objectNotFoundWithin( resolve.remainingNames.get( 0 ), SqlIdentifier.getString( resolve.path.stepNames() ) ) );
            }
        }

        // Failed to match.  If we're matching case-sensitively, try a more lenient match. If we find something we can offer a helpful hint.
        if ( nameMatcher.isCaseSensitive() ) {
            final SqlNameMatcher liberalMatcher = SqlNameMatchers.liberal();
            resolved.clear();
            parentScope.resolveTable( names, liberalMatcher, SqlValidatorScope.Path.EMPTY, resolved );
            if ( resolved.count() == 1 ) {
                final SqlValidatorScope.Resolve resolve = resolved.only();
                if ( resolve.remainingNames.isEmpty() || previousResolve == null ) {
                    // We didn't match it case-sensitive, so they must have had the right identifier, wrong case.
                    //
                    // If previousResolve is null, we matched nothing case-sensitive and everything case-insensitive, so the mismatch must have been at position 0.
                    final int i = previousResolve == null
                            ? 0
                            : previousResolve.path.stepCount();
                    final int offset = resolve.path.stepCount() + resolve.remainingNames.size() - names.size();
                    final List<String> prefix = resolve.path.stepNames().subList( 0, offset + i );
                    final String next = resolve.path.stepNames().get( i + offset );
                    if ( prefix.isEmpty() ) {
                        throw validator.newValidationError( id, Static.RESOURCE.objectNotFoundDidYouMean( names.get( i ), next ) );
                    } else {
                        throw validator.newValidationError( id, Static.RESOURCE.objectNotFoundWithinDidYouMean( names.get( i ), SqlIdentifier.getString( prefix ), next ) );
                    }
                } else {
                    throw validator.newValidationError( id, Static.RESOURCE.objectNotFoundWithin( resolve.remainingNames.get( 0 ), SqlIdentifier.getString( resolve.path.stepNames() ) ) );
                }
            }
        }
        throw validator.newValidationError( id, Static.RESOURCE.objectNotFound( id.getComponent( 0 ).toString() ) );
    }


    public RelDataType validateImpl( RelDataType targetRowType ) {
        resolvedNamespace = Objects.requireNonNull( resolveImpl( id ) );
        if ( resolvedNamespace instanceof TableNamespace ) {
            SqlValidatorTable table = resolvedNamespace.getTable();
            if ( validator.shouldExpandIdentifiers() ) {
                // TODO:  expand qualifiers for column references also
                List<String> qualifiedNames = table.getQualifiedName();
                if ( qualifiedNames != null ) {
                    // Assign positions to the components of the fully-qualified identifier, as best we can. We assume that qualification adds names to the front, e.g. FOO.BAR becomes BAZ.FOO.BAR.
                    List<SqlParserPos> poses = new ArrayList<>( Collections.nCopies( qualifiedNames.size(), id.getParserPosition() ) );
                    int offset = qualifiedNames.size() - id.names.size();

                    // Test offset in case catalog supports fewer qualifiers than catalog reader.
                    if ( offset >= 0 ) {
                        for ( int i = 0; i < id.names.size(); i++ ) {
                            poses.set( i + offset, id.getComponentParserPosition( i ) );
                        }
                    }
                    id.setNames( qualifiedNames, poses );
                }
            }
        }

        RelDataType rowType = resolvedNamespace.getRowType();

        if ( extendList != null ) {
            if ( !(resolvedNamespace instanceof TableNamespace) ) {
                throw new RuntimeException( "cannot convert" );
            }
            resolvedNamespace = ((TableNamespace) resolvedNamespace).extend( extendList );
            rowType = resolvedNamespace.getRowType();
        }

        // Build a list of monotonic expressions.
        final ImmutableList.Builder<Pair<SqlNode, SqlMonotonicity>> builder = ImmutableList.builder();
        List<RelDataTypeField> fields = rowType.getFieldList();
        for ( RelDataTypeField field : fields ) {
            final String fieldName = field.getName();
            final SqlMonotonicity monotonicity = resolvedNamespace.getMonotonicity( fieldName );
            if ( monotonicity != SqlMonotonicity.NOT_MONOTONIC ) {
                builder.add( Pair.of( (SqlNode) new SqlIdentifier( fieldName, SqlParserPos.ZERO ), monotonicity ) );
            }
        }
        monotonicExprs = builder.build();

        // Validation successful.
        return rowType;
    }


    public SqlIdentifier getId() {
        return id;
    }


    public SqlNode getNode() {
        return id;
    }


    @Override
    public SqlValidatorNamespace resolve() {
        assert resolvedNamespace != null : "must call validate first";
        return resolvedNamespace.resolve();
    }


    @Override
    public SqlValidatorTable getTable() {
        return resolvedNamespace == null ? null : resolve().getTable();
    }


    public List<Pair<SqlNode, SqlMonotonicity>> getMonotonicExprs() {
        return monotonicExprs;
    }


    @Override
    public SqlMonotonicity getMonotonicity( String columnName ) {
        final SqlValidatorTable table = getTable();
        return table.getMonotonicity( columnName );
    }


    @Override
    public boolean supportsModality( SqlModality modality ) {
        final SqlValidatorTable table = getTable();
        if ( table == null ) {
            return modality == SqlModality.RELATION;
        }
        return table.supportsModality( modality );
    }
}

