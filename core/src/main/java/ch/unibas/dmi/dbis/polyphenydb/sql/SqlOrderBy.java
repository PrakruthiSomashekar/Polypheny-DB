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

package ch.unibas.dmi.dbis.polyphenydb.sql;


import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserPos;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableNullableList;
import java.util.List;


/**
 * Parse tree node that represents an {@code ORDER BY} on a query other than a {@code SELECT} (e.g. {@code VALUES} or {@code UNION}).
 *
 * It is a purely syntactic operator, and is eliminated by {@link ch.unibas.dmi.dbis.polyphenydb.sql.validate.SqlValidatorImpl#performUnconditionalRewrites} and replaced with the ORDER_OPERAND of SqlSelect.
 */
public class SqlOrderBy extends SqlCall {

    public static final SqlSpecialOperator OPERATOR = new Operator() {
        @Override
        public SqlCall createCall( SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands ) {
            return new SqlOrderBy( pos, operands[0], (SqlNodeList) operands[1], operands[2], operands[3] );
        }
    };

    public final SqlNode query;
    public final SqlNodeList orderList;
    public final SqlNode offset;
    public final SqlNode fetch;


    public SqlOrderBy( SqlParserPos pos, SqlNode query, SqlNodeList orderList, SqlNode offset, SqlNode fetch ) {
        super( pos );
        this.query = query;
        this.orderList = orderList;
        this.offset = offset;
        this.fetch = fetch;
    }


    @Override
    public SqlKind getKind() {
        return SqlKind.ORDER_BY;
    }


    public SqlOperator getOperator() {
        return OPERATOR;
    }


    public List<SqlNode> getOperandList() {
        return ImmutableNullableList.of( query, orderList, offset, fetch );
    }


    /**
     * Definition of {@code ORDER BY} operator.
     */
    private static class Operator extends SqlSpecialOperator {

        private Operator() {
            // NOTE:  make precedence lower then SELECT to avoid extra parens
            super( "ORDER BY", SqlKind.ORDER_BY, 0 );
        }


        public SqlSyntax getSyntax() {
            return SqlSyntax.POSTFIX;
        }


        public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
            SqlOrderBy orderBy = (SqlOrderBy) call;
            final SqlWriter.Frame frame = writer.startList( SqlWriter.FrameTypeEnum.ORDER_BY );
            orderBy.query.unparse( writer, getLeftPrec(), getRightPrec() );
            if ( orderBy.orderList != SqlNodeList.EMPTY ) {
                writer.sep( getName() );
                final SqlWriter.Frame listFrame = writer.startList( SqlWriter.FrameTypeEnum.ORDER_BY_LIST );
                unparseListClause( writer, orderBy.orderList );
                writer.endList( listFrame );
            }
            if ( orderBy.offset != null ) {
                final SqlWriter.Frame frame2 = writer.startList( SqlWriter.FrameTypeEnum.OFFSET );
                writer.newlineAndIndent();
                writer.keyword( "OFFSET" );
                orderBy.offset.unparse( writer, -1, -1 );
                writer.keyword( "ROWS" );
                writer.endList( frame2 );
            }
            if ( orderBy.fetch != null ) {
                final SqlWriter.Frame frame3 = writer.startList( SqlWriter.FrameTypeEnum.FETCH );
                writer.newlineAndIndent();
                writer.keyword( "FETCH" );
                writer.keyword( "NEXT" );
                orderBy.fetch.unparse( writer, -1, -1 );
                writer.keyword( "ROWS" );
                writer.keyword( "ONLY" );
                writer.endList( frame3 );
            }
            writer.endList( frame );
        }
    }
}

